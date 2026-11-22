package com.conexaostreet.cpuzandroid;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses selected user-supplied ADB dump lines, never executes shell commands.
 * Input is untrusted and not a manufacturer-signed attestation.
 */
final class AdbEvidenceParser {
    private AdbEvidenceParser(){}
    private static final Pattern PROP=Pattern.compile("^\\[([^\\]]+)\\]: \\[([^\\]]*)\\]$");
    private static final Pattern KEY_VALUE=Pattern.compile("^\\s*([A-Za-z][A-Za-z ]{1,30}):\\s*([-+A-Za-z0-9._ ]{1,45})$");
    static LinkedHashMap<String,String> parse(InputStream raw) throws Exception {
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        int lines=0;int total=0;int thermal=0;
        try(BufferedReader br=new BufferedReader(new InputStreamReader(raw,StandardCharsets.UTF_8))){
            String line;
            while((line=br.readLine())!=null){
                if(++lines>4000 || (total+=line.length())>300000)break;
                String trimmed=line.trim();
                if(trimmed.length()>220)continue;
                String lower=trimmed.toLowerCase(Locale.ROOT);
                if(lower.matches(".*(serial|imei|meid|imsi|iccid|account|token|secret|password|mac_address|wifi\\.mac|bluetooth\\.address).*"))continue;
                Matcher prop=PROP.matcher(trimmed);
                if(prop.matches()){
                    String key=prop.group(1),val=prop.group(2);
                    if(val.length()>100)continue;
                    if(key.equals("ro.boot.verifiedbootstate")||key.equals("ro.boot.flash.locked")
                        ||key.equals("ro.boot.vbmeta.device_state")||key.equals("ro.soc.model")
                        ||key.equals("ro.product.model")||key.equals("ro.product.manufacturer")
                        ||key.equals("ro.hardware")){
                        out.put("ADB prop "+key,val);
                    }
                    continue;
                }
                Matcher kv=KEY_VALUE.matcher(trimmed);
                if(kv.matches()){
                    String key=kv.group(1).trim().toLowerCase(Locale.ROOT);
                    if(key.equals("level")||key.equals("scale")||key.equals("temperature")
                      ||key.equals("voltage")||key.equals("health")||key.equals("status")
                      ||key.equals("charge counter")||key.equals("cycle count")){
                        out.put("ADB bateria "+key,kv.group(2));
                    }
                }
                if(thermal<20 && (trimmed.contains("Temperature{")||trimmed.contains("mValue=")||
                    trimmed.contains("Temperature{mName="))){
                    // Only extracted thermal readings; preserve raw format to avoid unit mistakes.
                    String clean=trimmed.replaceAll("[^a-zA-Z0-9 ._:=+\\-{},]","");
                    if(clean.length()>155)clean=clean.substring(0,155);
                    out.put("ADB leitura térmica "+(++thermal),clean);
                }
            }
        }
        out.put("Fonte e confiança","Texto importado manualmente; não é prova criptográfica nem certificação de peças.");
        out.put("Privacidade","Linhas com identificadores sensíveis conhecidos foram ignoradas; consulte o arquivo original antes de compartilhá-lo.");
        if(out.size()==2)out.put("Resultado","Nenhuma linha compatível encontrada. Gere dumpsys battery, dumpsys thermalservice e getprop.");
        return out;
    }
}
