package com.conexaostreet.cpuzandroid;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Compares Android-visible specifications to sourced OEM catalog records.
 * A match is not proof of an original component; a mismatch is not proof of a fake.
 */
final class ComponentComparator {
    private ComponentComparator(){}
    static String compare(JSONArray references,LinkedHashMap<String,LinkedHashMap<String,String>> snapshot)
        throws Exception{
        if(references.length()==0)return "Nenhuma referência oficial cadastrada para este modelo. Não é possível comparar.";
        StringBuilder out=new StringBuilder();
        out.append("COMPATIBILIDADE COM ESPECIFICAÇÕES OFICIAIS\n");
        out.append("Não certifica originalidade nem identifica troca física.\n\n");
        for(int i=0;i<references.length();i++){
            JSONObject row=references.getJSONObject(i);
            String section=row.optString("component_category");
            JSONObject expected=row.optJSONObject("expected_attributes");
            if(expected==null)continue;
            out.append(section.toUpperCase(Locale.ROOT)).append("\n");
            if(section.equals("tela")){
                String resolution=expected.optString("resolution_px","");
                String observed=get(snapshot,"Tela","Resolução física");
                String[] parts=resolution.split("x");
                if(parts.length==2 && observed!=null){
                    boolean same=observed.contains(parts[0])&&observed.contains(parts[1]);
                    out.append(same?"  Resolução: compatível com a ficha técnica.\n"
                        :"  Resolução: divergência aparente; conferir escala/configuração de tela.\n");
                    out.append("  Leitura Android: ").append(observed).append("\n");
                }else out.append("  Resolução: não verificável.\n");
                String available=get(snapshot,"Tela - modos detectados","Modos divulgados pelo driver");
                int hz=expected.optInt("refresh_max_hz",0);
                if(hz>0&&available!=null){
                    boolean found=available.contains("@"+hz+".0Hz")||available.contains("@"+hz+"Hz");
                    out.append(found?"  Modo máximo oficial encontrado entre os modos expostos.\n"
                        :"  Modo máximo oficial não encontrado (pode haver limitação de software).\n");
                }else out.append("  Frequência máxima: não verificável.\n");
            }else if(section.equals("processador")){
                String cores=get(snapshot,"Processador","Núcleos disponíveis");
                if(cores!=null && expected.has("cpu_cores")){
                    int found;
                    try{found=Integer.parseInt(cores.trim());}catch(Exception e){found=-1;}
                    out.append(found==expected.optInt("cpu_cores")?
                        "  Núcleos lógicos compatíveis.\n":"  Contagem lógica diferente ou indisponível; não prova defeito.\n");
                }
                out.append("  SoC declarado pelo Android: ")
                    .append(get(snapshot,"Processador","Modelo do SoC")).append("\n");
                out.append("  SoC de referência: ").append(expected.optString("soc")).append("\n");
                out.append("  A nomenclatura de firmware pode ser diferente do nome comercial.\n");
            }else if(section.equals("bateria")){
                String raw=get(snapshot,"Dados OEM acessíveis da bateria","charge_full_design (sysfs, valor bruto)");
                long value=0;
                try{value=Long.parseLong(raw);}catch(Exception ignored){}
                int nominal=expected.optInt("design_capacity_mah",0);
                if(value>=100000 && value<100000000 && nominal>0){
                    double current=value/1000.0;
                    double deviation=Math.abs(current-nominal)/nominal;
                    out.append(String.format(Locale.getDefault(),
                      "  Capacidade declarada pelo driver: %.0f mAh (supondo unidade µAh).\n",current));
                    out.append(deviation<=0.10?"  Valor próximo da especificação de fábrica.\n":
                        "  Diferença de capacidade nominal; verificar a unidade OEM antes de concluir.\n");
                }else out.append("  Capacidade de projeto não disponibilizada em unidade confiável.\n");
                out.append("  Saúde/autenticidade exigem evidências adicionais.\n");
            }else{
                out.append("  Referência: ").append(expected.toString()).append("\n");
                out.append("  Identificadores físicos específicos não são expostos de modo confiável.\n");
            }
            out.append("  Fonte: ").append(row.optString("official_source_url")).append("\n\n");
        }
        out.append("RESULTADO: comparação indicativa, NÃO é laudo de peça original ou falsificada.");
        return out.toString();
    }
    private static String get(LinkedHashMap<String,LinkedHashMap<String,String>> scan,String section,String key){
        if(scan==null || scan.get(section)==null)return null;
        return scan.get(section).get(key);
    }
}
