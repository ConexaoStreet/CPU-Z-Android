package com.conexaostreet.cpuzandroid;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(11,17,26),CARD=Color.rgb(22,32,45),EDGE=Color.rgb(40,57,72);
    private static final int TEXT=Color.rgb(232,243,246),MUTED=Color.rgb(164,182,190),ACCENT=Color.rgb(98,214,203);
    private static final int REQ_MIC=100,REQ_CAMERA=101,REQ_GPS=102,REQ_PHOTO=103,REQ_PDF=201,REQ_JSON=202;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String[] testNames={"Tela - cores e pixels","Toque e multitouch","Alto-falante","Vibração",
        "Microfone","Câmera","Lanterna / flash","GPS","Acelerômetro","Botões de volume","Porta de carregamento"};
    private LinkedHashMap<String,LinkedHashMap<String,String>> snapshot;
    private SharedPreferences prefs;
    private CloudClient cloud;
    private LinearLayout body;
    private String flashId=null;
    private CameraManager flashManager=null;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("diagnostics",MODE_PRIVATE);
        cloud=new CloudClient();
        refresh();
    }
    @Override protected void onPause(){super.onPause();turnOffFlash();}
    private void refresh(){snapshot=DeviceInspector.inspect(this);render();}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable fill(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView label(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(17),dp(22),dp(17),dp(34));scroll.addView(body);
        TextView title=label("CPU-Z  Android",28,TEXT,true);body.addView(title);
        TextView subtitle=label("Diagnóstico local de hardware • "+Build.MODEL,13,MUTED,false);
        subtitle.setPadding(0,dp(4),0,dp(18));body.addView(subtitle);
        addSummary();
        LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(action("Atualizar",()->refresh(),1));
        buttons.addView(action("PDF",()->saveReport(REQ_PDF),1));
        buttons.addView(action("JSON",()->saveReport(REQ_JSON),1));
        body.addView(buttons);
        LinearLayout extra=new LinearLayout(this);
        extra.setOrientation(LinearLayout.HORIZONTAL);
        extra.addView(action("Salvar local",()->saveLocal(),1));
        extra.addView(action("Comparar",()->compareLocal(),1));
        extra.addView(action("Neon",()->CloudDialog.open(this,cloud,snapshot,testResults()),1));
        body.addView(extra);
        TextView caution=label("Atenção: nenhum app Android comum consegue atestar automaticamente se uma peça foi trocada ou é original. Resultados não verificáveis são identificados.",12,MUTED,false);
        caution.setPadding(dp(7),dp(16),dp(7),dp(14));body.addView(caution);
        sectionTitle("TESTES DE FUNCIONAMENTO");
        addTest("Tela - cores e pixels","Inspeção visual de cores, manchas e pixels",this::screenTest);
        addTest("Toque e multitouch","Teste de pontos de toque",this::touchTest);
        addTest("Alto-falante","Reprodução de sinal de áudio",this::speakerTest);
        addTest("Vibração","Ativação do motor de vibração",this::vibrationTest);
        addTest("Microfone","Captura temporária de nível sonoro",this::microphoneTest);
        addTest("Câmera","Abre o aplicativo de câmera para teste",this::cameraTest);
        addTest("Lanterna / flash","Acende o flash brevemente",this::flashTest);
        addTest("GPS","Solicita uma localização atual sem gravar coordenadas",this::gpsTest);
        addTest("Acelerômetro","Verifica recebimento de amostras do sensor",this::motionTest);
        addTest("Botões de volume","Teste guiado dos botões",()->confirm("Botões de volume","Pressione os botões de volume para cima e para baixo. Os dois responderam?"));
        addTest("Porta de carregamento","Verifica sinal de alimentação reportado pelo Android",this::usbTest);
        sectionTitle("INVENTÁRIO DE HARDWARE");
        for(Map.Entry<String,LinkedHashMap<String,String>> section:snapshot.entrySet()){
            LinearLayout card=card();
            TextView heading=label(section.getKey(),17,ACCENT,true);
            heading.setPadding(0,0,0,dp(8));card.addView(heading);
            for(Map.Entry<String,String> item:section.getValue().entrySet()){
                TextView key=label(item.getKey(),12,MUTED,false);key.setPadding(0,dp(9),0,dp(2));card.addView(key);
                TextView val=label(item.getValue(),13,TEXT,false);card.addView(val);
            }
            body.addView(card);
        }
        setContentView(scroll);
    }
    private void addSummary(){
        LinearLayout card=card();
        TextView h=label("VISÃO GERAL",12,ACCENT,true);card.addView(h);
        String battery=snapshot.get("Bateria")==null?"Indisponível":snapshot.get("Bateria").get("Temperatura (bateria)");
        String thermal=snapshot.get("Temperatura e desempenho")==null?"Indisponível":snapshot.get("Temperatura e desempenho").get("Estado térmico do sistema");
        String charge=snapshot.get("Bateria")==null?"Não informado":snapshot.get("Bateria").get("Carga");
        TextView value=label((battery==null?"Temperatura não exposta":battery)+"  •  "+(charge==null?"":charge),20,TEXT,true);
        value.setPadding(0,dp(12),0,dp(5));card.addView(value);
        TextView desc=label("Estado térmico: "+(thermal==null?"Indisponível":thermal),13,MUTED,false);card.addView(desc);
        if(thermal!=null&&(thermal.contains("Elevado")||thermal.contains("Crítico")||thermal.contains("Emergência")||thermal.contains("Desligamento"))){
            TextView warning=label("ATENÇÃO: restrição térmica informada. Interrompa tarefas pesadas e o carregamento até esfriar.",13,Color.rgb(255,178,117),true);
            warning.setPadding(0,dp(12),0,0);card.addView(warning);
        }
        body.addView(card);
    }
    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(15),dp(15),dp(15),dp(15));
        GradientDrawable bg=fill(CARD,14);bg.setStroke(dp(1),EDGE);l.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);
        l.setLayoutParams(lp);return l;
    }
    private void sectionTitle(String title){
        TextView t=label(title,12,MUTED,true);t.setPadding(dp(3),dp(18),0,dp(11));body.addView(t);
    }
    private Button action(String text,Runnable onClick,int weight){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(13);
        b.setTextColor(BG);b.setBackground(fill(ACCENT,10));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(47),weight);
        p.setMargins(dp(3),dp(2),dp(3),dp(2));b.setLayoutParams(p);
        b.setOnClickListener(v->onClick.run());return b;
    }
    private void addTest(String name,String detail,Runnable action){
        LinearLayout c=card();c.setOnClickListener(v->action.run());
        TextView t=label(name+"    ›",15,TEXT,true);c.addView(t);
        TextView sub=label(detail,12,MUTED,false);sub.setPadding(0,dp(5),0,dp(5));c.addView(sub);
        TextView result=label(prefs.getString("test_"+name,"Não testado"),11,ACCENT,false);c.addView(result);
        body.addView(c);
    }
    private void record(String name,String result){
        String time=new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date());
        prefs.edit().putString("test_"+name,result+" • "+time).apply();refresh();
    }
    private void confirm(String name,String question){
        new AlertDialog.Builder(this).setTitle(name).setMessage(question)
            .setPositiveButton("Funcionou",(d,w)->record(name,"Funcionou (confirmado pelo usuário)"))
            .setNegativeButton("Não funcionou",(d,w)->record(name,"Falhou (confirmado pelo usuário)"))
            .setNeutralButton("Não sei",null).show();
    }
    private void info(String name,String message){new AlertDialog.Builder(this).setTitle(name).setMessage(message).setPositiveButton("OK",null).show();}
    private void screenTest(){
        final String name="Tela - cores e pixels";
        Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        TextView field=label("",19,Color.WHITE,true);field.setGravity(Gravity.CENTER);
        root.addView(field,new LinearLayout.LayoutParams(-1,0,1));
        Button next=new Button(this);next.setText("Próxima cor");root.addView(next,new LinearLayout.LayoutParams(-1,dp(66)));
        final int[] colors={Color.BLACK,Color.WHITE,Color.RED,Color.GREEN,Color.BLUE};
        final String[] labels={"PRETO","BRANCO","VERMELHO","VERDE","AZUL"};
        final int[] idx={0};
        Runnable update=()->{
            field.setBackgroundColor(colors[idx[0]]);
            field.setTextColor(idx[0]==1||idx[0]==3?Color.BLACK:Color.WHITE);
            field.setText(labels[idx[0]]+"\n\nObserve pixels mortos, manchas e linhas");
            next.setText(idx[0]==colors.length-1?"Concluir teste":"Próxima cor");
        };
        next.setOnClickListener(v->{
            if(idx[0]<colors.length-1){idx[0]++;update.run();}
            else{dialog.dismiss();confirm(name,"A tela exibiu corretamente todas as cores, sem defeitos visíveis?");}
        });
        dialog.setContentView(root);update.run();dialog.show();
    }
    private void touchTest(){
        final String name="Toque e multitouch";
        Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        final int[] events={0},maxPointers={0};
        View surface=new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                canvas.drawColor(BG);
                p.setColor(EDGE);p.setStrokeWidth(dp(1));
                for(int x=0;x<getWidth();x+=dp(45))canvas.drawLine(x,0,x,getHeight(),p);
                for(int y=0;y<getHeight();y+=dp(45))canvas.drawLine(0,y,getWidth(),y,p);
                p.setTextSize(dp(20));p.setColor(ACCENT);p.setStyle(Paint.Style.FILL);
                canvas.drawText("Toques detectados: "+events[0],dp(18),dp(44),p);
                canvas.drawText("Máx. dedos: "+maxPointers[0],dp(18),dp(75),p);
            }
            @Override public boolean onTouchEvent(MotionEvent e){
                events[0]++;maxPointers[0]=Math.max(maxPointers[0],e.getPointerCount());invalidate();return true;
            }
        };
        root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        Button done=new Button(this);done.setText("Concluir teste");
        done.setOnClickListener(v->{dialog.dismiss();confirm(name,"Você conseguiu tocar em todas as regiões? Máximo observado: "+maxPointers[0]+" dedo(s).");});
        root.addView(done,new LinearLayout.LayoutParams(-1,dp(65)));
        dialog.setContentView(root);dialog.show();
    }
    private void speakerTest(){
        final String name="Alto-falante";
        try{
            ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_MUSIC,70);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP,800);
            handler.postDelayed(tone::release,1000);
            handler.postDelayed(()->confirm(name,"Você ouviu um som nítido pelo alto-falante?"),1100);
        }catch(Exception e){info(name,"Não foi possível reproduzir o som: "+e.getMessage());}
    }
    private void vibrationTest(){
        final String name="Vibração";
        try{
            Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);
            if(v==null||!v.hasVibrator()){record(name,"Vibrador não encontrado");return;}
            v.vibrate(VibrationEffect.createOneShot(650,VibrationEffect.DEFAULT_AMPLITUDE));
            handler.postDelayed(()->confirm(name,"O celular vibrou?"),850);
        }catch(Exception e){info(name,"Teste indisponível: "+e.getMessage());}
    }
    private boolean permission(String name,int req){
        if(checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED)return true;
        requestPermissions(new String[]{name},req);return false;
    }
    private void microphoneTest(){
        if(!permission(Manifest.permission.RECORD_AUDIO,REQ_MIC))return;
        new AlertDialog.Builder(this).setTitle("Microfone").setMessage("Fale ou bata palmas durante os próximos dois segundos. O áudio será analisado apenas em memória, sem gravação de arquivo.")
            .setPositiveButton("Iniciar",(d,w)->recordAudio()).setNegativeButton("Cancelar",null).show();
    }
    private void recordAudio(){
        new Thread(()->{
            AudioRecord recorder=null;
            int peak=0,readTotal=0;
            String failure=null;
            try{
                int rate=16000, size=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(size<=0)throw new IllegalStateException("Formato de áudio não suportado");
                recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,4096));
                if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Gravador não inicializou");
                short[] buf=new short[1024];
                recorder.startRecording();long deadline=SystemClock.elapsedRealtime()+2000;
                while(SystemClock.elapsedRealtime()<deadline){
                    int n=recorder.read(buf,0,buf.length);
                    if(n<0)throw new IllegalStateException("Falha de leitura "+n);
                    readTotal+=n;
                    for(int i=0;i<n;i++)peak=Math.max(peak,Math.abs((int)buf[i]));
                }
            }catch(Exception e){failure=e.getMessage();}
            finally{if(recorder!=null){try{if(recorder.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)recorder.stop();}catch(Exception ignored){}recorder.release();}}
            final int p=peak,samples=readTotal;final String error=failure;
            runOnUiThread(()->{
                if(error!=null){info("Microfone","Teste indisponível: "+error);return;}
                confirm("Microfone","Amostras lidas: "+samples+". Pico do sinal: "+p+" de 32767. O nível mudou quando você falou? Valor baixo pode indicar ambiente silencioso ou restrição de áudio.");
            });
        },"mic-test").start();
    }
    private void cameraTest(){
        if(!permission(Manifest.permission.CAMERA,REQ_CAMERA))return;
        try{
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if(i.resolveActivity(getPackageManager())==null){info("Câmera","Nenhum aplicativo de câmera disponível");return;}
            startActivityForResult(i,REQ_PHOTO);
        }catch(Exception e){info("Câmera","Não foi possível abrir: "+e.getMessage());}
    }
    private void flashTest(){
        if(!permission(Manifest.permission.CAMERA,REQ_CAMERA))return;
        try{
            flashManager=(CameraManager)getSystemService(CAMERA_SERVICE);flashId=null;
            for(String id:flashManager.getCameraIdList()){
                CameraCharacteristics cc=flashManager.getCameraCharacteristics(id);
                Boolean flash=cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing=cc.get(CameraCharacteristics.LENS_FACING);
                if(Boolean.TRUE.equals(flash)&&facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK){flashId=id;break;}
            }
            if(flashId==null){info("Lanterna / flash","Nenhuma lanterna traseira foi exposta ao app");return;}
            flashManager.setTorchMode(flashId,true);
            handler.postDelayed(()->{turnOffFlash();confirm("Lanterna / flash","A lanterna acendeu e apagou normalmente?");},1300);
        }catch(Exception e){turnOffFlash();info("Lanterna / flash","Teste indisponível: "+e.getMessage());}
    }
    private void turnOffFlash(){
        if(flashManager!=null&&flashId!=null){try{flashManager.setTorchMode(flashId,false);}catch(Exception ignored){}}
        flashId=null;
    }
    private void gpsTest(){
        if(!permission(Manifest.permission.ACCESS_FINE_LOCATION,REQ_GPS))return;
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(lm==null||!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){info("GPS","Ative a localização e tente novamente, preferencialmente em um local aberto.");return;}
        if(Build.VERSION.SDK_INT<30){info("GPS","Leitura automática de localização disponível nesta versão do aplicativo para Android 11 ou superior.");return;}
        info("GPS","Solicitando uma posição atual por GPS. Se estiver em local fechado, a obtenção de sinal pode falhar.");
        try{
            CancellationSignal cancellation=new CancellationSignal();
            AtomicBoolean complete=new AtomicBoolean(false);
            Runnable timeout=()->{
                if(complete.compareAndSet(false,true)){cancellation.cancel();info("GPS","Sem localização atual após 15 s. Isso não prova defeito; tente ao ar livre.");}
            };
            handler.postDelayed(timeout,15000);
            lm.getCurrentLocation(LocationManager.GPS_PROVIDER,cancellation,getMainExecutor(),(Location loc)->{
                if(!complete.compareAndSet(false,true))return;
                handler.removeCallbacks(timeout);
                if(loc==null){info("GPS","O sistema não forneceu localização atual.");return;}
                String accuracy=loc.hasAccuracy()?String.format(Locale.getDefault(),"%.1f metros",loc.getAccuracy()):"não informada";
                record("GPS","Localização obtida (precisão "+accuracy+"); coordenadas omitidas");
                info("GPS","Localização obtida pelo provedor GPS. Precisão estimada: "+accuracy+". As coordenadas não são armazenadas.");
            });
        }catch(Exception e){info("GPS","Não foi possível consultar o GPS: "+e.getMessage());}
    }
    private void motionTest(){
        SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor s=sm==null?null:sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(s==null){record("Acelerômetro","Não detectado pelo Android");return;}
        final int[] samples={0};
        SensorEventListener listener=new SensorEventListener(){
            @Override public void onSensorChanged(SensorEvent e){samples[0]++;}
            @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
        };
        if(!sm.registerListener(listener,s,SensorManager.SENSOR_DELAY_UI)){info("Acelerômetro","Falha ao iniciar leitura");return;}
        Toast.makeText(this,"Movimente o celular suavemente por 2 segundos",Toast.LENGTH_LONG).show();
        handler.postDelayed(()->{
            sm.unregisterListener(listener);
            if(samples[0]>0)record("Acelerômetro","Recebeu "+samples[0]+" amostras");
            else record("Acelerômetro","Nenhuma amostra recebida");
            info("Acelerômetro","Amostras recebidas: "+samples[0]+". A presença de amostras não garante precisão ou calibração.");
        },2100);
    }
    private void usbTest(){
        Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
        if(plugged!=0){
            confirm("Porta de carregamento","O Android detecta uma fonte de energia conectada agora. O cabo e o conector estão firmes, sem falhas de contato?");
        }else{
            info("Porta de carregamento","Conecte um carregador seguro, aguarde o Android reconhecer a alimentação e toque no teste novamente. Isso não avalia desgaste físico da porta.");
        }
    }
    private void saveReport(int kind){
        String suffix=new SimpleDateFormat("yyyyMMdd_HHmm",Locale.ROOT).format(new Date());
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(kind==REQ_PDF?"application/pdf":"application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"CPUZ_diagnostico_"+suffix+(kind==REQ_PDF?".pdf":".json"));
        try{startActivityForResult(intent,kind);}catch(Exception e){info("Relatório","Seletor de arquivos indisponível: "+e.getMessage());}
    }
    private void saveLocal(){
        try{
            LocalHistory.save(this,snapshot,testResults());
            info("Histórico local","Diagnóstico salvo apenas no armazenamento privado deste aplicativo.");
        }catch(Exception e){info("Histórico local","Não foi possível salvar: "+e.getMessage());}
    }
    private void compareLocal(){
        try{info("Comparação com diagnóstico anterior",LocalHistory.summary(this,snapshot));}
        catch(Exception e){info("Histórico local","Não foi possível comparar: "+e.getMessage());}
    }
    private LinkedHashMap<String,String> testResults(){
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        for(String name:testNames)out.put(name,prefs.getString("test_"+name,"Não testado"));
        return out;
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==REQ_PHOTO){
            if(result==RESULT_OK)confirm("Câmera","A câmera abriu e conseguiu capturar uma imagem corretamente?");
            else info("Câmera","Teste cancelado ou sem confirmação de captura.");
            return;
        }
        if((req==REQ_PDF||req==REQ_JSON)&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try(OutputStream stream=getContentResolver().openOutputStream(uri)){
                if(stream==null)throw new IllegalStateException("Não foi possível abrir o destino");
                LinkedHashMap<String,LinkedHashMap<String,String>> scan=DeviceInspector.inspect(this);
                if(req==REQ_PDF)ReportWriter.pdf(stream,scan,testResults());
                else ReportWriter.json(stream,scan,testResults());
                new AlertDialog.Builder(this).setTitle("Relatório salvo")
                    .setMessage("Arquivo criado no local que você escolheu. Deseja compartilhar?")
                    .setPositiveButton("Compartilhar",(d,w)->share(uri,req))
                    .setNegativeButton("Fechar",null).show();
            }catch(Exception e){info("Relatório","Falha ao salvar: "+e.getMessage());}
        }
    }
    private void share(Uri uri,int req){
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType(req==REQ_PDF?"application/pdf":"application/json");
        i.putExtra(Intent.EXTRA_STREAM,uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(i,"Compartilhar diagnóstico"));}
        catch(Exception e){info("Compartilhamento","Não foi possível compartilhar: "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){
        super.onRequestPermissionsResult(request,perms,grants);
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){
            if(request==REQ_MIC)microphoneTest();
            else if(request==REQ_CAMERA)info("Permissão de câmera","Permissão concedida. Toque novamente em Câmera ou Lanterna para iniciar o teste desejado.");
            else if(request==REQ_GPS)gpsTest();
        }else info("Permissão","Permissão não concedida. O teste correspondente não pôde ser executado.");
    }
}
