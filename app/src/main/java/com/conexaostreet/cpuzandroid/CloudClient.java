package com.conexaostreet.cpuzandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Opt-in Neon Auth / PostgREST integration.
 * Credentials and HTTP-only session cookies are held in memory for this process only.
 * No database password, admin keys, or service secrets are shipped with the APK.
 */
final class CloudClient {
    private static final String AUTH="https://ep-wandering-truth-bbd0q2mq.neonauth.c-15.us-east-1.aws.neon.tech/neondb/auth";
    private static final String DATA="https://ep-wandering-truth-bbd0q2mq.apirest.c-15.us-east-1.aws.neon.tech/neondb/rest/v1";
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
    private String jwt;
    private String email;
    boolean loggedIn(){return jwt!=null && !jwt.isEmpty();}
    String accountEmail(){return email==null?"Sem sessão":email;}

    void signUp(String email,String password) throws Exception {
        JSONObject payload=new JSONObject().put("email",email).put("password",password).put("name",email.split("@")[0]);
        request(true,"POST","/sign-up/email",payload.toString(),null);
    }
    void verifyOtp(String email,String code) throws Exception {
        JSONObject payload=new JSONObject().put("email",email).put("otp",code);
        request(true,"POST","/email-otp/verify-email",payload.toString(),null);
    }
    void signIn(String email,String password) throws Exception {
        jwt=null;
        JSONObject payload=new JSONObject().put("email",email).put("password",password);
        String resp=request(true,"POST","/sign-in/email",payload.toString(),null);
        if(resp!=null&&!resp.trim().isEmpty()){
            JSONObject body=new JSONObject(resp);
            JSONObject session=body.optJSONObject("session");
            if(session!=null)jwt=session.optString("access_token",null);
        }
        refreshJwt();
        if(!loggedIn())throw new IllegalStateException("A sessão não retornou JWT para acesso seguro aos dados.");
        this.email=email;
    }
    private void refreshJwt() throws Exception {
        String resp=request(true,"GET","/get-session",null,null);
        // The Better Auth JWT plugin returns Set-Auth-Jwt header; request() populates jwt.
        if(!loggedIn() && resp!=null){
            JSONObject obj=new JSONObject(resp);
            JSONObject session=obj.optJSONObject("session");
            if(session!=null)jwt=session.optString("access_token",null);
        }
    }
    void signOut() throws Exception {
        try{request(true,"POST","/sign-out","{}",null);}
        finally{jwt=null;email=null;cookies.getCookieStore().removeAll();}
    }
    private String request(boolean auth,String method,String path,String body,String prefer) throws Exception {
        if(!auth && !loggedIn())throw new IllegalStateException("Faça login antes de acessar a nuvem.");
        String endpoint=auth?AUTH:DATA;
        URL url=new URL(endpoint+path);
        if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IllegalStateException("Conexão insegura bloqueada");
        URI uri=url.toURI();
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        if(prefer!=null)connection.setRequestProperty("Prefer",prefer);
        if(!auth)connection.setRequestProperty("Authorization","Bearer "+jwt);
        if(auth){
            Map<String,List<String>> cookieHeaders=cookies.get(uri,Collections.emptyMap());
            for(Map.Entry<String,List<String>> h:cookieHeaders.entrySet()){
                if(h.getKey()!=null)for(String value:h.getValue())connection.addRequestProperty(h.getKey(),value);
            }
        }
        if(body!=null){
            connection.setDoOutput(true);
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            if(bytes.length>450000)throw new IllegalArgumentException("Relatório muito grande. Exporte localmente e reduza os dados antes do envio.");
            try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
        }
        int status=connection.getResponseCode();
        if(auth){
            cookies.put(uri,connection.getHeaderFields());
            String token=connection.getHeaderField("set-auth-jwt");
            if(token!=null && !token.isEmpty())jwt=token;
        }
        InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
        String response="";
        if(stream!=null){
            try(InputStream in=stream;ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                byte[] chunk=new byte[4096];int n;
                while((n=in.read(chunk))!=-1){
                    if(buffer.size()+n>1048576)throw new IllegalStateException("Resposta do servidor muito grande");
                    buffer.write(chunk,0,n);
                }
                response=buffer.toString("UTF-8");
            }
        }
        connection.disconnect();
        if(status<200||status>=300){
            String message="HTTP "+status;
            try{
                JSONObject error=new JSONObject(response);
                String detail=error.optString("message",error.optString("error",""));
                if(!detail.isEmpty())message+=": "+detail.substring(0,Math.min(150,detail.length()));
            }catch(Exception ignored){}
            if(status==401){jwt=null;message+=" (sessão expirada ou não autorizada)";}
            throw new IllegalStateException(message);
        }
        return response;
    }

    void setCloudConsent(boolean enabled) throws Exception {
        JSONObject data=new JSONObject().put("cloud_sync_enabled",enabled).put("anonymous_analytics_enabled",false);
        request(false,"POST","/user_preferences?on_conflict=owner_id",data.toString(),"resolution=merge-duplicates,return=minimal");
    }
    private String installationId(Context c){
        SharedPreferences prefs=c.getSharedPreferences("cloud_installation",Context.MODE_PRIVATE);
        String value=prefs.getString("installation_id",null);
        if(value!=null)return value;
        value=UUID.randomUUID().toString();
        prefs.edit().putString("installation_id",value).apply();
        return value;
    }
    private static JSONObject mapJson(Map<String,String> values)throws Exception{
        JSONObject o=new JSONObject();
        for(Map.Entry<String,String> v:values.entrySet())o.put(v.getKey(),v.getValue());
        return o;
    }
    private static JSONObject nestedJson(LinkedHashMap<String,LinkedHashMap<String,String>> scan)throws Exception{
        JSONObject o=new JSONObject();
        for(Map.Entry<String,LinkedHashMap<String,String>> e:scan.entrySet())o.put(e.getKey(),mapJson(e.getValue()));
        return o;
    }
    private static String resultStatus(String result){
        if(result==null||result.startsWith("Não testado"))return "not_tested";
        if(result.startsWith("Funcionou")||result.startsWith("Recebeu")||result.startsWith("Localização obtida"))return "pass";
        if(result.startsWith("Falhou")||result.startsWith("Nenhuma amostra"))return "fail";
        return "inconclusive";
    }
    String sync(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> scan,
             LinkedHashMap<String,String> tests) throws Exception{
        if(!loggedIn())throw new IllegalStateException("Faça login primeiro.");
        setCloudConsent(true); // Called only after the user accepts the sync confirmation UI.
        JSONObject dev=new JSONObject()
            .put("installation_id",installationId(c))
            .put("manufacturer",Build.MANUFACTURER)
            .put("model",Build.MODEL)
            .put("hardware",Build.HARDWARE)
            .put("android_version",Build.VERSION.RELEASE)
            .put("soc",Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:Build.HARDWARE);
        String devResp=request(false,"POST",
            "/devices?on_conflict=owner_id,installation_id&select=id",
            dev.toString(),"resolution=merge-duplicates,return=representation");
        JSONArray devs=new JSONArray(devResp);
        if(devs.length()==0)throw new IllegalStateException("O servidor não retornou o aparelho cadastrado");
        String deviceId=devs.getJSONObject(0).getString("id");
        LinkedHashMap<String,String> battery=scan.get("Bateria");
        Double temp=null;
        if(battery!=null){
            String raw=battery.get("Temperatura (bateria)");
            if(raw!=null){
                try{temp=Double.parseDouble(raw.replace("°C","").trim().replace(",","."));}catch(NumberFormatException ignored){}
            }
        }
        String therm=scan.containsKey("Temperatura e desempenho")?
            scan.get("Temperatura e desempenho").get("Estado térmico do sistema"):"";
        int thermal=therm==null?-1:
            therm.startsWith("Desligamento")?6:therm.startsWith("Emergência")?5:
            therm.startsWith("Crítico")?4:therm.startsWith("Elevado")?3:
            therm.startsWith("Moderado")?2:therm.startsWith("Leve")?1:therm.startsWith("Sem")?0:-1;
        JSONObject summary=new JSONObject()
            .put("source","Android API, dados OEM legíveis e testes guiados")
            .put("originality","not_verifiable")
            .put("privacy","opt-in")
            .put("test_total",tests.size());
        JSONObject run=new JSONObject()
            .put("device_id",deviceId)
            .put("client_version","0.2.0")
            .put("schema_version",1)
            .put("status",tests.values().stream().anyMatch(v->v.startsWith("Não testado"))?"partial":"completed")
            .put("snapshot",nestedJson(scan))
            .put("summary",summary);
        if(temp!=null)run.put("battery_temperature_c",temp);
        if(thermal>=0)run.put("thermal_severity",thermal);
        JSONArray created=new JSONArray(request(false,"POST","/diagnostic_runs?select=id",run.toString(),"return=representation"));
        if(created.length()==0)throw new IllegalStateException("Diagnóstico não registrado");
        String runId=created.getJSONObject(0).getString("id");
        JSONArray resultRows=new JSONArray();
        for(Map.Entry<String,String> test:tests.entrySet()){
            boolean automatic=test.getKey().equals("Acelerômetro")||test.getKey().equals("GPS");
            resultRows.put(new JSONObject()
                .put("run_id",runId)
                .put("test_name",test.getKey())
                .put("status",resultStatus(test.getValue()))
                .put("method",automatic?"automatic":"guided_user_confirmation")
                .put("evidence",new JSONObject().put("result_text",test.getValue())));
        }
        if(resultRows.length()>0)request(false,"POST","/test_results",resultRows.toString(),"return=minimal");
        JSONArray parts=new JSONArray();
        String[] sections={"Peças e originalidade","Dados OEM acessíveis da bateria","Câmeras - dados técnicos","Tela - modos detectados"};
        for(String section:sections){
            LinkedHashMap<String,String> items=scan.get(section);
            if(items==null)continue;
            for(Map.Entry<String,String> item:items.entrySet()){
                parts.put(new JSONObject()
                    .put("run_id",runId).put("category",section).put("label",item.getKey())
                    .put("source",section.equals("Peças e originalidade")?"android_limitation":"device_probe")
                    .put("observation",section.equals("Peças e originalidade")?"not_accessible":"detected")
                    .put("authenticity","not_verifiable")
                    .put("evidence",new JSONObject().put("value",item.getValue())));
            }
        }
        if(parts.length()>0)request(false,"POST","/component_observations",parts.toString(),"return=minimal");
        if(temp!=null){
            JSONObject metric=new JSONObject()
                .put("device_id",deviceId).put("run_id",runId)
                .put("metric","battery_temperature").put("value",temp).put("unit","celsius")
                .put("provenance","BatteryManager.EXTRA_TEMPERATURE");
            request(false,"POST","/telemetry_samples",metric.toString(),"return=minimal");
        }
        return runId;
    }
    String thermalTrend(Context c) throws Exception{
        JSONObject payload=new JSONObject().put("p_installation_id",installationId(c));
        String answer=request(false,"POST","/rpc/my_thermal_trend",payload.toString(),null);
        JSONObject j=new JSONObject(answer);
        if(j.optInt("samples",0)==0)return "Nenhuma amostra térmica sincronizada para esta instalação.";
        return "Amostras: "+j.optInt("samples")+"\\n"
            +"Mínima (bateria): "+j.optString("min_battery_c","indisponível")+" °C\\n"
            +"Média (bateria): "+j.optString("avg_battery_c","indisponível")+" °C\\n"
            +"Máxima (bateria): "+j.optString("max_battery_c","indisponível")+" °C\\n"
            +"Estados térmicos elevados: "+j.optInt("severe_thermal_events")+"\\n\\n"
            +j.optString("interpretation");
    }
    String recentScans() throws Exception{
        String resp=request(false,"GET","/diagnostic_runs?select=id,captured_at,status,battery_temperature_c,thermal_severity&order=captured_at.desc&limit=15",null,null);
        JSONArray rows=new JSONArray(resp);if(rows.length()==0)return "Nenhum diagnóstico salvo ainda.";
        StringBuilder result=new StringBuilder();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            result.append(row.optString("captured_at","Data desconhecida"));
            result.append(" • ").append(row.optString("status",""));
            if(!row.isNull("battery_temperature_c"))result.append(" • Bateria ").append(row.optString("battery_temperature_c")).append("°C");
            result.append("\n");
        }
        return result.toString();
    }
    String officialReference() throws Exception{
        String model=Build.MODEL.toLowerCase(Locale.ROOT).replace("motorola","").trim();
        String manufacturer=Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String path="/hardware_catalog?select=component_category,expected_attributes,official_source_url"
            +"&manufacturer=ilike."+java.net.URLEncoder.encode(manufacturer,"UTF-8")
            +"&model=ilike."+java.net.URLEncoder.encode(model,"UTF-8")
            +"&order=component_category.asc";
        JSONArray rows=new JSONArray(request(false,"GET",path,null,null));
        if(rows.length()==0)return "Nenhuma ficha técnica oficial cadastrada para este modelo. Isso não indica defeito.";
        StringBuilder out=new StringBuilder("Referência de fábrica, não prova de originalidade.\n\n");
        for(int i=0;i<rows.length();i++){
            JSONObject item=rows.getJSONObject(i);
            out.append(item.getString("component_category")).append(": ")
                .append(item.getJSONObject("expected_attributes").toString()).append("\n\n");
        }
        out.append("Fonte: ").append(rows.getJSONObject(0).optString("official_source_url"));
        return out.toString();
    }
    void deleteAllMyScans() throws Exception {
        request(false,"DELETE","/devices?id=not.is.null",null,"return=minimal");
        request(false,"DELETE","/user_preferences?owner_id=not.is.null",null,"return=minimal");
    }
}
