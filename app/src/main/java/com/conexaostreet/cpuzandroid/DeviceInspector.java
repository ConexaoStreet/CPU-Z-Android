package com.conexaostreet.cpuzandroid;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DeviceInspector {
    private DeviceInspector() {}
    public static final String DISCLAIMER = "Autenticidade de peças: NÃO VERIFICÁVEL por APIs públicas do Android. Funcionamento, marca informada e presença do componente NÃO provam originalidade.";
    public static LinkedHashMap<String, LinkedHashMap<String,String>> inspect(Context c) {
        LinkedHashMap<String,LinkedHashMap<String,String>> out = new LinkedHashMap<>();
        put(out,"Identificação","Data da leitura",DateFormat.getDateTimeInstance().format(new Date()));
        put(out,"Identificação","Fabricante",clean(Build.MANUFACTURER));
        put(out,"Identificação","Marca",clean(Build.BRAND));
        put(out,"Identificação","Modelo",clean(Build.MODEL));
        put(out,"Identificação","Dispositivo",clean(Build.DEVICE));
        put(out,"Identificação","Placa (identificador)",clean(Build.BOARD));
        put(out,"Identificação","Hardware declarado",clean(Build.HARDWARE));
        put(out,"Sistema e segurança","Versão Android",Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")");
        put(out,"Sistema e segurança","Build",clean(Build.DISPLAY));
        put(out,"Sistema e segurança","Patch de segurança",clean(Build.VERSION.SECURITY_PATCH));
        put(out,"Sistema e segurança","Tempo desde reinício",String.format(Locale.getDefault(),"%.1f h",SystemClock.elapsedRealtime()/3600000.0));
        put(out,"Processador","Núcleos disponíveis",String.valueOf(Runtime.getRuntime().availableProcessors()));
        put(out,"Processador","Arquiteturas (ABI)",String.join(", ",Build.SUPPORTED_ABIS));
        if (Build.VERSION.SDK_INT >= 31) {
            put(out,"Processador","Fabricante do SoC",clean(Build.SOC_MANUFACTURER));
            put(out,"Processador","Modelo do SoC",clean(Build.SOC_MODEL));
        }
        ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        if(am!=null) {
            ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            put(out,"Memória e armazenamento","RAM total",bytes(info.totalMem));
            put(out,"Memória e armazenamento","RAM disponível",bytes(info.availMem));
            put(out,"Memória e armazenamento","Memória em nível baixo",info.lowMemory?"Sim":"Não");
        }
        try {
            StatFs fs=new StatFs(Environment.getDataDirectory().getAbsolutePath());
            put(out,"Memória e armazenamento","Partição /data - capacidade",bytes(fs.getTotalBytes()));
            put(out,"Memória e armazenamento","Partição /data - livre",bytes(fs.getAvailableBytes()));
        } catch(Exception e) {put(out,"Memória e armazenamento","Armazenamento","Indisponível");}
        try {
            WindowManager wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
            Display disp=wm.getDefaultDisplay();
            DisplayMetrics dm=new DisplayMetrics();
            disp.getRealMetrics(dm);
            put(out,"Tela","Resolução física",dm.widthPixels+" × "+dm.heightPixels+" px");
            put(out,"Tela","Densidade",dm.densityDpi+" dpi");
            put(out,"Tela","Taxa ativa de atualização",String.format(Locale.getDefault(),"%.1f Hz",disp.getRefreshRate()));
        } catch(Exception e){put(out,"Tela","Dados de tela","Indisponíveis");}
        readBattery(c,out);
        readThermal(c,out);
        readCamera(c,out);
        readSensors(c,out);
        readConnections(c,out);
        for(String piece:new String[]{"Tela / painel","Bateria","Câmeras","Placa lógica / processador","Porta USB-C","Alto-falantes e microfones","Sensores","Memória e armazenamento"}) {
            put(out,"Peças e originalidade",piece,"NÃO VERIFICÁVEL (necessita dados/autenticação do fabricante ou inspeção técnica)");
        }
        AdvancedHardwareInspector.add(c,out);
        return out;
    }
    static void put(LinkedHashMap<String,LinkedHashMap<String,String>> out,String section,String k,String v){
        if(!out.containsKey(section))out.put(section,new LinkedHashMap<>());
        out.get(section).put(k,v);
    }
    static String clean(String s){return s==null||s.trim().isEmpty()||s.equalsIgnoreCase("unknown")?"Não informado":s;}
    static String bytes(long n){return String.format(Locale.getDefault(),"%.2f GB",n/(1024.0*1024*1024));}
    private static void readBattery(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(b==null){put(out,"Bateria","Estado","Não disponível");return;}
        int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        if(level>=0&&scale>0)put(out,"Bateria","Carga",Math.round(level*100f/scale)+"%");
        int tenths=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
        if(tenths!=Integer.MIN_VALUE)put(out,"Bateria","Temperatura (bateria)",String.format(Locale.getDefault(),"%.1f °C",tenths/10f));
        int mv=b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1);
        if(mv>0)put(out,"Bateria","Tensão",String.format(Locale.getDefault(),"%.3f V",mv/1000f));
        int health=b.getIntExtra(BatteryManager.EXTRA_HEALTH,-1);
        String condition;
        switch(health){
            case BatteryManager.BATTERY_HEALTH_GOOD: condition="Boa (informada pelo Android)";break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:condition="SOBREAQUECIMENTO";break;
            case BatteryManager.BATTERY_HEALTH_DEAD:condition="Falha";break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:condition="Sobretensão";break;
            case BatteryManager.BATTERY_HEALTH_COLD:condition="Fria";break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:condition="Falha não especificada";break;
            default:condition="Desconhecida / não exposta";
        }
        put(out,"Bateria","Saúde reportada pelo sistema",condition);
        int st=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        put(out,"Bateria","Carregamento",st==BatteryManager.BATTERY_STATUS_CHARGING?"Carregando":st==BatteryManager.BATTERY_STATUS_FULL?"Completa":st==BatteryManager.BATTERY_STATUS_DISCHARGING?"Descarregando":st==BatteryManager.BATTERY_STATUS_NOT_CHARGING?"Conectada, não carregando":"Desconhecido");
        put(out,"Bateria","Tecnologia",clean(b.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)));
        int plug=b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
        put(out,"Bateria","Fonte de energia",plug==BatteryManager.BATTERY_PLUGGED_USB?"USB":plug==BatteryManager.BATTERY_PLUGGED_AC?"Tomada":plug==BatteryManager.BATTERY_PLUGGED_WIRELESS?"Indução":"Bateria / outro");
        if(Build.VERSION.SDK_INT>=34){
            int cycles=b.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT,-1);
            put(out,"Bateria","Ciclos (se expostos pelo fabricante)",cycles>=0?String.valueOf(cycles):"Não informado pelo sistema");
        } else put(out,"Bateria","Ciclos","Não disponíveis nesta versão/API");
        BatteryManager bm=(BatteryManager)c.getSystemService(Context.BATTERY_SERVICE);
        if(bm!=null){
            try{
                int ma=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if(ma!=Integer.MIN_VALUE)put(out,"Bateria","Corrente instantânea",String.format(Locale.getDefault(),"%.0f mA",ma/1000.0));
            }catch(Exception ignored){}
            try{
                int charge=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                if(charge>0)put(out,"Bateria","Carga restante estimada",String.format(Locale.getDefault(),"%.0f mAh",charge/1000.0));
            }catch(Exception ignored){}
        }
    }
    private static void readThermal(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
        if(pm==null||Build.VERSION.SDK_INT<29){put(out,"Temperatura e desempenho","Estado térmico","Indisponível");return;}
        int s=pm.getCurrentThermalStatus();
        String[] names={"Sem limitação","Leve","Moderado","Elevado","Crítico","Emergência","Desligamento iminente"};
        put(out,"Temperatura e desempenho","Estado térmico do sistema",s>=0&&s<names.length?names[s]:"Indeterminado ("+s+")");
        put(out,"Temperatura e desempenho","Temperatura da CPU/GPU","Não exposta por API pública confiável neste dispositivo");
    }
    private static void readCamera(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        CameraManager manager=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);
        if(manager==null){put(out,"Câmeras","Informações","Não disponíveis");return;}
        try{
            String[] ids=manager.getCameraIdList();
            put(out,"Câmeras","Interfaces de câmera detectadas",String.valueOf(ids.length)+" (não equivale ao número de lentes)");
            for(String id:ids){
                try{
                    CameraCharacteristics cc=manager.getCameraCharacteristics(id);
                    Integer face=cc.get(CameraCharacteristics.LENS_FACING);
                    String direction=face==null?"indefinida":face==CameraCharacteristics.LENS_FACING_FRONT?"frontal":face==CameraCharacteristics.LENS_FACING_BACK?"traseira":"externa";
                    Boolean flash=cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    put(out,"Câmeras","ID "+id, direction+" • flash: "+(flash==null?"não informado":flash?"sim":"não"));
                }catch(Exception e){put(out,"Câmeras","ID "+id,"Características restritas pelo sistema");}
            }
        }catch(Exception e){put(out,"Câmeras","Informações","Indisponíveis: "+e.getClass().getSimpleName());}
    }
    private static void readSensors(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        SensorManager sm=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        if(sm==null){put(out,"Sensores","Inventário","Não disponível");return;}
        List<Sensor> sensors=sm.getSensorList(Sensor.TYPE_ALL);
        put(out,"Sensores","Sensores expostos pelo sistema",sensors.size()+" (detecção não comprova funcionamento)");
        int ix=1;
        for(Sensor s:sensors){
            put(out,"Sensores",String.format(Locale.ROOT,"%02d",ix++)+" • "+s.getName(),
                "Tipo "+s.getType()+" • fornecedor declarado: "+clean(s.getVendor())+" • resolução "+s.getResolution());
        }
    }
    private static void readConnections(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        PackageManager pm=c.getPackageManager();
        put(out,"Conectividade","Bluetooth",pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)?"Hardware declarado":"Não declarado");
        put(out,"Conectividade","Wi-Fi",pm.hasSystemFeature(PackageManager.FEATURE_WIFI)?"Hardware declarado":"Não declarado");
        put(out,"Conectividade","NFC",pm.hasSystemFeature(PackageManager.FEATURE_NFC)?"Hardware declarado":"Não declarado");
        put(out,"Conectividade","GPS",pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)?"Hardware declarado":"Não declarado");
        put(out,"Conectividade","Leitor biométrico",pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)?"Hardware declarado":"Não declarado");
        put(out,"Conectividade","Telefonia",pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)?"Hardware declarado":"Não declarado");
        try{
            ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities cap=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
            String active=cap==null?"Sem rede ativa informada":cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Dados móveis":cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"Ethernet":"Outra rede";
            put(out,"Conectividade","Rede atualmente usada",active);
        }catch(Exception e){put(out,"Conectividade","Rede atualmente usada","Indisponível");}
    }
}
