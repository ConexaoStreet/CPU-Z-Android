package com.conexaostreet.cpuzandroid;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

public final class ReportWriter {
    private ReportWriter() {}
    public static void json(OutputStream stream, LinkedHashMap<String,LinkedHashMap<String,String>> scan,
                            Map<String,String> tests) throws Exception {
        JSONObject doc=new JSONObject();
        doc.put("aplicativo","CPU-Z Android");
        doc.put("versao_relatorio","1.0");
        doc.put("aviso_originalidade",DeviceInspector.DISCLAIMER);
        JSONObject sections=new JSONObject();
        for(Map.Entry<String,LinkedHashMap<String,String>> sec:scan.entrySet()){
            JSONObject values=new JSONObject();
            for(Map.Entry<String,String> item:sec.getValue().entrySet())values.put(item.getKey(),item.getValue());
            sections.put(sec.getKey(),values);
        }
        doc.put("dados",sections);
        JSONObject t=new JSONObject();
        for(Map.Entry<String,String> item:tests.entrySet())t.put(item.getKey(),item.getValue());
        doc.put("testes",t);
        stream.write(doc.toString(2).getBytes(StandardCharsets.UTF_8));
        stream.flush();
    }

    public static void pdf(OutputStream stream, LinkedHashMap<String,LinkedHashMap<String,String>> scan,
                           Map<String,String> tests) throws IOException {
        PdfLayout layout=new PdfLayout();
        try{
            layout.title("CPU-Z Android | Diagnóstico geral");
            layout.text("Informações lidas do Android e resultados de testes guiados. Ausência de erro NÃO comprova peça original.");
            layout.text(DeviceInspector.DISCLAIMER);
            for(Map.Entry<String,LinkedHashMap<String,String>> sec:scan.entrySet()){
                layout.section(sec.getKey());
                for(Map.Entry<String,String> item:sec.getValue().entrySet()){
                    layout.entry(item.getKey(),item.getValue());
                }
            }
            layout.section("Testes de funcionamento");
            for(Map.Entry<String,String> test:tests.entrySet())layout.entry(test.getKey(),test.getValue());
            layout.section("Limitações do laudo");
            layout.text("Sem acesso privilegiado ao fabricante, o app não certifica substituição, autenticidade, desempenho nominal ou integridade de componentes. Testes confirmados pelo usuário são identificados como tais.");
            layout.finish();
            layout.doc.writeTo(stream);
        }finally{layout.doc.close();}
    }

    private static final class PdfLayout {
        final PdfDocument doc=new PdfDocument();
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PdfDocument.Page page;
        Canvas canvas;
        int y=0;
        int pageNum=0;
        final int LEFT=40, RIGHT=555, BOTTOM=789;
        void newPage(){
            if(page!=null)doc.finishPage(page);
            pageNum++;
            PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,pageNum).create();
            page=doc.startPage(info);
            canvas=page.getCanvas();
            canvas.drawColor(Color.WHITE);
            y=45;
            paint.setColor(Color.rgb(52,74,84));paint.setTextSize(9);paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("CPU-Z ANDROID  •  RELATÓRIO LOCAL",LEFT,24,paint);
            canvas.drawText("Página "+pageNum,RIGHT-47,824,paint);
        }
        void ensure(int height){if(page==null||y+height>BOTTOM)newPage();}
        void title(String value){
            ensure(45); paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            paint.setTextSize(18);paint.setColor(Color.rgb(21,50,59));
            canvas.drawText(value,LEFT,y,paint);y+=30;
            paint.setStrokeWidth(2);paint.setColor(Color.rgb(47,164,157));
            canvas.drawLine(LEFT,y-9,RIGHT,y-9,paint);y+=9;
        }
        void section(String value){
            ensure(39);y+=12;
            paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            paint.setTextSize(13);paint.setColor(Color.rgb(15,103,102));
            canvas.drawText(value,LEFT,y,paint);y+=20;
        }
        void entry(String k,String v){
            text(k+": "+v);
        }
        void text(String input){
            if(input==null)input="";
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            paint.setTextSize(10.2f);paint.setColor(Color.rgb(38,48,55));
            String[] words=input.replace('\n',' ').split("\\s+");
            StringBuilder line=new StringBuilder();
            for(String w:words) {
                String candidate=line.length()==0?w:line+" "+w;
                if(paint.measureText(candidate)>RIGHT-LEFT && line.length()>0){
                    draw(line.toString());line.setLength(0);
                    if(paint.measureText(w)>RIGHT-LEFT){
                        for(int p=0;p<w.length();){
                            int end=p+1;
                            while(end<=w.length()&&paint.measureText(w.substring(p,end))<RIGHT-LEFT)end++;
                            end=Math.max(p+1,end-1);
                            draw(w.substring(p,end));p=end;
                        }
                        continue;
                    }
                }
                if(line.length()>0)line.append(' ');
                line.append(w);
            }
            if(line.length()>0)draw(line.toString());
            y+=4;
        }
        void draw(String line){
            ensure(16);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            paint.setTextSize(10.2f);paint.setColor(Color.rgb(38,48,55));
            canvas.drawText(line,LEFT,y,paint);y+=15;
        }
        void finish(){if(page==null)newPage();doc.finishPage(page);page=null;}
    }
}
