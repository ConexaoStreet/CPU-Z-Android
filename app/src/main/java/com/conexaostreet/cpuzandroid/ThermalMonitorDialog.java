package com.conexaostreet.cpuzandroid;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/**
 * On-screen thermal monitor: passive reading only, does not stress hardware.
 */
final class ThermalMonitorDialog {
    private ThermalMonitorDialog(){}
    static void show(Activity a){
        Dialog dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout layout=new LinearLayout(a);layout.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(18*a.getResources().getDisplayMetrics().density);
        layout.setPadding(pad,pad,pad,pad);
        layout.setBackgroundColor(Color.rgb(11,17,26));
        TextView title=new TextView(a);title.setText("Monitor térmico em tempo real");
        title.setTextSize(20);title.setTextColor(Color.WHITE);layout.addView(title);
        TextView value=new TextView(a);value.setTextSize(29);value.setTextColor(Color.rgb(98,214,203));
        value.setPadding(0,pad,0,pad/2);layout.addView(value);
        TextView note=new TextView(a);note.setTextSize(13);note.setTextColor(Color.LTGRAY);
        note.setText("Leitura passiva da bateria. Não mede temperatura da CPU nem certifica defeito.");
        layout.addView(note);
        ArrayList<Float> readings=new ArrayList<>();
        ChartView graph=new ChartView(a,readings);
        layout.addView(graph,new LinearLayout.LayoutParams(-1,(int)(210*a.getResources().getDisplayMetrics().density)));
        TextView stats=new TextView(a);stats.setTextColor(Color.LTGRAY);stats.setTextSize(13);
        stats.setPadding(0,pad/2,0,pad);layout.addView(stats);
        Button close=new Button(a);close.setText("Fechar monitor");layout.addView(close);
        dialog.setContentView(layout);
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-2);
        close.setOnClickListener(v->dialog.dismiss());
        Handler h=new Handler(Looper.getMainLooper());
        final boolean[] alive={true};
        final float[] min={Float.MAX_VALUE},max={-Float.MAX_VALUE};
        Runnable[] task=new Runnable[1];
        task[0]=()->{
            if(!alive[0]||a.isFinishing()||a.isDestroyed())return;
            Intent b=a.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(b!=null){
                int raw=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
                if(raw!=Integer.MIN_VALUE){
                    float temp=raw/10f;
                    readings.add(temp);if(readings.size()>90)readings.remove(0);
                    min[0]=Math.min(min[0],temp);max[0]=Math.max(max[0],temp);
                    value.setText(String.format(Locale.getDefault(),"%.1f °C",temp));
                    String status="Indisponível";
                    if(Build.VERSION.SDK_INT>=29){
                        try{
                            PowerManager pm=(PowerManager)a.getSystemService(Activity.POWER_SERVICE);
                            status=String.valueOf(pm.getCurrentThermalStatus());
                        }catch(Exception ignored){}
                    }
                    stats.setText(String.format(Locale.getDefault(),
                        "Mín: %.1f °C  •  Máx: %.1f °C\nEstado térmico Android: %s\nAmostras: %d (intervalo 2 s)",
                        min[0],max[0],status,readings.size()));
                    if(temp>=45f) {
                        note.setTextColor(Color.rgb(255,169,113));
                        note.setText("Temperatura da bateria elevada: interrompa jogos e carregamento até esfriar. Esta faixa é orientativa.");
                    }
                    graph.invalidate();
                }else value.setText("Sensor indisponível");
            }
            h.postDelayed(task[0],2000);
        };
        dialog.setOnDismissListener(v->{alive[0]=false;h.removeCallbacks(task[0]);});
        dialog.show();h.post(task[0]);
    }
    private static final class ChartView extends View {
        final ArrayList<Float> points;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        ChartView(Activity a,ArrayList<Float> values){super(a);points=values;}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            int w=getWidth(),h=getHeight();
            if(w<=0||h<=0)return;
            int left=36,top=22,right=w-18,bottom=h-27;
            p.setColor(Color.rgb(40,60,70));p.setStrokeWidth(1);p.setStyle(Paint.Style.STROKE);
            for(int i=0;i<=4;i++){
                float y=top+(bottom-top)*i/4f;
                c.drawLine(left,y,right,y,p);
            }
            p.setColor(Color.LTGRAY);p.setTextSize(24);p.setStyle(Paint.Style.FILL);
            c.drawText("Bateria (°C)",left,16,p);
            if(points.size()<2){c.drawText("Coletando dados…",left+8,(top+bottom)/2f,p);return;}
            float low=Float.MAX_VALUE,high=-Float.MAX_VALUE;
            for(float v:points){low=Math.min(low,v);high=Math.max(high,v);}
            low=(float)Math.floor(Math.min(low,25f)-2);
            high=(float)Math.ceil(Math.max(high,45f)+2);
            if(high-low<5)high=low+5;
            p.setColor(Color.rgb(98,214,203));p.setStrokeWidth(4);p.setStyle(Paint.Style.STROKE);
            float dx=(right-left)/(float)(Math.max(points.size()-1,1));
            for(int i=1;i<points.size();i++){
                float x1=left+dx*(i-1),x2=left+dx*i;
                float y1=bottom-(points.get(i-1)-low)/(high-low)*(bottom-top);
                float y2=bottom-(points.get(i)-low)/(high-low)*(bottom-top);
                c.drawLine(x1,y1,x2,y2,p);
            }
            p.setStyle(Paint.Style.FILL);p.setColor(Color.LTGRAY);p.setTextSize(22);
            c.drawText(String.format(Locale.getDefault(),"%.0f°",high),left+2,top+25,p);
            c.drawText(String.format(Locale.getDefault(),"%.0f°",low),left+2,bottom-8,p);
        }
    }
}
