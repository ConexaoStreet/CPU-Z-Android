package com.conexaostreet.cpuzandroid;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.BatteryManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only, best-effort hardware probes.
 *
 * No root, hidden APIs, device identifiers, or privilege escalation.
 * Every system-file value names its evidence source and remains 'not available' if denied.
 */
final class AdvancedHardwareInspector {
    private AdvancedHardwareInspector() {}
    static void add(Context context, LinkedHashMap<String,LinkedHashMap<String,String>> out) {
        thermal(context,out);
        batteryNodes(out);
        cpuNodes(out);
        displayModes(context,out);
        cameraDetails(context,out);
        audio(context,out);
        software(out);
    }
    private static void add(LinkedHashMap<String,LinkedHashMap<String,String>> o,String k,String name,String value){
        DeviceInspector.put(o,k,name,value);
    }
    private static String file(String absolutePath){
        File f=new File(absolutePath);
        if(!f.isFile() || !f.canRead())return null;
        try(BufferedReader br=new BufferedReader(new FileReader(f))){
            String s=br.readLine();
            if(s==null)return null;
            s=s.trim();
            if(s.length()==0 || s.length()>180)return null;
            for(int i=0;i<s.length();i++){
                char c=s.charAt(i);
                if(c<32 && c!='\t')return null;
            }
            return s;
        }catch(Exception e){return null;}
    }
    private static void thermal(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        if(Build.VERSION.SDK_INT>=30){
            try{
                PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
                float headroom=pm.getThermalHeadroom(10);
                if(!Float.isNaN(headroom)&&!Float.isInfinite(headroom)&&headroom>=0){
                    add(out,"Análise térmica avançada","Margem térmica relativa (API Android)",
                        String.format(Locale.US,"%.3f (1,0 = limite de throttling severo; valor instantâneo)",headroom));
                } else add(out,"Análise térmica avançada","Margem térmica","Não fornecida pelo Android");
            }catch(Exception e){add(out,"Análise térmica avançada","Margem térmica","Restringida pelo sistema");}
        }
        add(out,"Análise térmica avançada","CPU/GPU em °C","Não existem APIs públicas universais para essas leituras; os sensores térmicos HAL exigem acesso autorizado pelo sistema/OEM");
    }
    private static void batteryNodes(LinkedHashMap<String,LinkedHashMap<String,String>> out){
        // Whitelisted power_supply paths. Never try unauthorized filesystem access.
        String[] names={"cycle_count","charge_full","charge_full_design","energy_full","energy_full_design",
            "voltage_now","current_now","temp","health","technology","status","capacity","manufacturer"};
        for(String n:names){
            String v=file("/sys/class/power_supply/battery/"+n);
            if(v!=null)add(out,"Dados OEM acessíveis da bateria",n+" (sysfs, valor bruto)",v);
        }
        String full=file("/sys/class/power_supply/battery/charge_full");
        String design=file("/sys/class/power_supply/battery/charge_full_design");
        if(full!=null&&design!=null){
            try{
                double f=Double.parseDouble(full),d=Double.parseDouble(design);
                if(d>0&&f>=0&&f/d<5){
                    add(out,"Dados OEM acessíveis da bateria","Relação capacidade atual/projeto",
                        String.format(Locale.US,"%.1f%% (estimativa de campos OEM; pode ser imprecisa)",100*f/d));
                }
            }catch(NumberFormatException ignored){}
        }else{
            add(out,"Dados OEM acessíveis da bateria","Capacidade real/projeto",
                "Não exposta por arquivos legíveis; não inferir degradação por autonomia");
        }
        for(String node:new String[]{"usb/online","usb/voltage_now","usb/current_now",
            "ac/online","wireless/online"}){
            String v=file("/sys/class/power_supply/"+node);
            if(v!=null)add(out,"Alimentação e conectores","sysfs "+node,v+" (valor bruto; unidade depende do driver)");
        }
    }
    private static void cpuNodes(LinkedHashMap<String,LinkedHashMap<String,String>> out){
        int visible=0;
        for(int i=0;i<32;i++){
            String base="/sys/devices/system/cpu/cpu"+i+"/cpufreq/";
            String cur=file(base+"scaling_cur_freq");
            String max=file(base+"cpuinfo_max_freq");
            if(cur!=null || max!=null){
                visible++;
                String a=cur==null?"não informado":cur+" kHz";
                String b=max==null?"não informado":max+" kHz";
                add(out,"Frequências de CPU","CPU "+i+" (sysfs)","Atual: "+a+" • Máxima exposta: "+b);
            }
        }
        if(visible==0)add(out,"Frequências de CPU","Leituras cpufreq","Arquivos do kernel inacessíveis para aplicativos comuns neste Android");
        String cpu=file("/proc/cpuinfo");
        if(cpu!=null)add(out,"Frequências de CPU","Primeira linha de /proc/cpuinfo",cpu);
    }
    private static void displayModes(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        try{
            WindowManager wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
            if(wm==null)return;
            Display d=wm.getDefaultDisplay();
            Display.Mode active=d.getMode();
            StringBuilder modes=new StringBuilder();
            for(Display.Mode m:d.getSupportedModes()){
                if(modes.length()>0)modes.append("; ");
                modes.append(m.getPhysicalWidth()).append("x").append(m.getPhysicalHeight()).append("@");
                modes.append(String.format(Locale.US,"%.1f",m.getRefreshRate())).append("Hz");
                if(modes.length()>850){modes.append("…");break;}
            }
            add(out,"Tela - modos detectados","Modo ativo",active.getPhysicalWidth()+"x"+active.getPhysicalHeight()+" @ "+active.getRefreshRate()+" Hz");
            add(out,"Tela - modos detectados","Modos divulgados pelo driver",modes.toString());
            add(out,"Tela - modos detectados","Identidade de painel","Identificador de fábrica e serial não expostos por API pública");
        }catch(Exception e){add(out,"Tela - modos detectados","Modos","Não disponíveis");}
    }
    private static void cameraDetails(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        try{
            CameraManager cm=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);
            if(cm==null)return;
            for(String id:cm.getCameraIdList()){
                try{
                    CameraCharacteristics cc=cm.getCameraCharacteristics(id);
                    Size array=cc.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                    float[] focal=cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    Set<String> physical=Build.VERSION.SDK_INT>=28?cc.getPhysicalCameraIds():java.util.Collections.emptySet();
                    add(out,"Câmeras - dados técnicos","Câmera ID "+id+" / matriz",
                        array==null?"Não declarada":array.getWidth()+"x"+array.getHeight()+" px");
                    add(out,"Câmeras - dados técnicos","Câmera ID "+id+" / lentes físicas",
                        physical.isEmpty()?"IDs físicos não expostos":physical.size()+" IDs lógicos associados (sem garantia de lentes reais)");
                    if(focal!=null && focal.length>0){
                        StringBuilder f=new StringBuilder();
                        for(float x:focal){if(f.length()>0)f.append(", ");f.append(String.format(Locale.US,"%.2f mm",x));}
                        add(out,"Câmeras - dados técnicos","Câmera ID "+id+" / distâncias focais",f.toString());
                    }
                }catch(Exception ignored){}
            }
        }catch(Exception e){add(out,"Câmeras - dados técnicos","Inventário","Restringido");}
    }
    private static void audio(Context c,LinkedHashMap<String,LinkedHashMap<String,String>> out){
        try{
            AudioManager a=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
            if(a==null)return;
            AudioDeviceInfo[] device=a.getDevices(AudioManager.GET_DEVICES_ALL);
            int output=0,input=0;
            for(AudioDeviceInfo item:device){if(item.isSource())input++;if(item.isSink())output++;}
            add(out,"Áudio - roteamento","Dispositivos de entrada reconhecidos",String.valueOf(input));
            add(out,"Áudio - roteamento","Dispositivos de saída reconhecidos",String.valueOf(output));
        }catch(Exception ignored){}
    }
    private static void software(LinkedHashMap<String,LinkedHashMap<String,String>> out){
        add(out,"Integridade observável","Assinatura da compilação (Build.TAGS)",DeviceInspector.clean(Build.TAGS));
        add(out,"Integridade observável","Estado de boot/AVB","Não é certificação verificável por este app sem atestado criptográfico e validação por servidor");
        add(out,"Integridade observável","Autenticidade de peças","Não verificável: diferenças de software e hardware apenas indicam necessidade de análise");
    }
}
