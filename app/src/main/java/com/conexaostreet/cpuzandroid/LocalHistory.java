package com.conexaostreet.cpuzandroid;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class LocalHistory {
    private LocalHistory(){}
    private static File file(Context c){return new File(c.getFilesDir(),"diagnostics_history.json");}
    private static JSONArray load(Context c) throws Exception {
        File f=file(c);if(!f.isFile())return new JSONArray();
        if(f.length()>1500000)throw new IllegalStateException("Histórico local maior que o esperado");
        try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream b=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;
            while((n=in.read(bytes))!=-1)b.write(bytes,0,n);
            return new JSONArray(b.toString("UTF-8"));
        }
    }
    static void save(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> snapshot,
                     Map<String,String> tests) throws Exception{
        JSONArray history=load(c);
        JSONObject record=new JSONObject();
        record.put("saved_at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT).format(new Date()));
        JSONObject scans=new JSONObject();
        for(Map.Entry<String,LinkedHashMap<String,String>> section:snapshot.entrySet()){
            JSONObject data=new JSONObject();
            for(Map.Entry<String,String> row:section.getValue().entrySet())data.put(row.getKey(),row.getValue());
            scans.put(section.getKey(),data);
        }
        JSONObject checks=new JSONObject();
        for(Map.Entry<String,String> t:tests.entrySet())checks.put(t.getKey(),t.getValue());
        record.put("snapshot",scans);record.put("tests",checks);
        JSONArray updated=new JSONArray();updated.put(record);
        for(int i=0;i<history.length()&&i<39;i++)updated.put(history.get(i));
        File target=file(c),tmp=new File(c.getFilesDir(),"diagnostics_history.tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){
            out.write(updated.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if(!tmp.renameTo(target))throw new IllegalStateException("Falha ao salvar histórico privado");
    }
    static String summary(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> current) throws Exception {
        JSONArray saved=load(c);
        if(saved.length()==0)return "Nenhum diagnóstico foi salvo localmente. Toque em 'Salvar local' para guardar uma medição.";
        StringBuilder text=new StringBuilder();
        text.append("Registros no aparelho: ").append(saved.length()).append("\n\n");
        JSONObject previous=saved.getJSONObject(0);
        text.append("Última medição: ").append(previous.optString("saved_at")).append("\n");
        JSONObject old=previous.optJSONObject("snapshot");
        if(old!=null){
            JSONObject battery=old.optJSONObject("Bateria");
            String currentTemp=current.get("Bateria")==null?null:current.get("Bateria").get("Temperatura (bateria)");
            if(battery!=null){
                text.append("Temperatura anterior: ").append(battery.optString("Temperatura (bateria)","Não informada")).append("\n");
                text.append("Temperatura agora: ").append(currentTemp==null?"Não informada":currentTemp).append("\n");
                text.append("Carga anterior: ").append(battery.optString("Carga","Não informada")).append("\n");
            }
            JSONObject th=old.optJSONObject("Temperatura e desempenho");
            if(th!=null)text.append("Estado térmico anterior: ").append(th.optString("Estado térmico do sistema","Não informado")).append("\n");
        }
        text.append("\nComparar leituras diferentes é útil para acompanhar tendências; não prova defeito ou originalidade de peças.");
        return text.toString();
    }
}
