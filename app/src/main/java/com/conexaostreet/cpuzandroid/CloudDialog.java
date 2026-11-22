package com.conexaostreet.cpuzandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Cloud is entirely optional. No network operation occurs on app launch.
 * The user must sign in AND confirm each diagnostic upload.
 */
final class CloudDialog {
    private CloudDialog() {}
    static void open(Activity host,CloudClient cloud,
                     LinkedHashMap<String,LinkedHashMap<String,String>> scan,
                     LinkedHashMap<String,String> tests){
        if(!cloud.loggedIn()){
            new AlertDialog.Builder(host).setTitle("Nuvem Neon (opcional)")
                .setMessage("Os testes funcionam offline. A conta só é necessária para sincronizar histórico entre dispositivos.")
                .setPositiveButton("Entrar",(d,w)->credentials(host,cloud,false,scan,tests))
                .setNeutralButton("Criar conta",(d,w)->credentials(host,cloud,true,scan,tests))
                .setNegativeButton("Cancelar",null).show();
            return;
        }
        String[] options={"Enviar diagnóstico agora","Ver histórico na nuvem","Analisar tendência térmica",
            "Consultar ficha técnica do modelo","Apagar todos os meus dados na nuvem","Sair da conta"};
        new AlertDialog.Builder(host).setTitle("Neon • "+cloud.accountEmail())
            .setItems(options,(d,w)->{
                switch(w){
                    case 0: consent(host,cloud,scan,tests);break;
                    case 1: work(host,"Histórico",cloud::recentScans,null);break;
                    case 2: work(host,"Tendência térmica",()->cloud.thermalTrend(host.getApplicationContext()),null);break;
                    case 3: work(host,"Comparação com ficha técnica",()->cloud.officialReference(scan),null);break;
                    case 4: delete(host,cloud);break;
                    case 5: work(host,"Sair",()->{cloud.signOut();return "Sessão encerrada.";},null);break;
                    default:break;
                }
            }).setNegativeButton("Fechar",null).show();
    }
    private static EditText text(Activity a,String hint,int type){
        EditText edit=new EditText(a);edit.setSingleLine(true);
        edit.setHint(hint);edit.setInputType(type);
        return edit;
    }
    private static LinearLayout fields(Activity a,EditText... e){
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
        int pad=Math.round(18*a.getResources().getDisplayMetrics().density);
        root.setPadding(pad,4,pad,4);
        for(EditText item:e)root.addView(item);
        return root;
    }
    private static void credentials(Activity host,CloudClient cloud,boolean register,
                                    LinkedHashMap<String,LinkedHashMap<String,String>> scan,
                                    LinkedHashMap<String,String> tests){
        EditText email=text(host,"Seu e-mail",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pwd=text(host,"Senha",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(host).setTitle(register?"Criar conta":"Entrar no Neon")
            .setView(fields(host,email,pwd))
            .setMessage("Senha e sessão não são gravadas no aparelho pelo CPU-Z Android. Uma nova entrada será necessária após fechar totalmente o app.")
            .setPositiveButton(register?"Criar conta":"Entrar",(d,w)->{
                String mail=email.getText().toString().trim();
                String pass=pwd.getText().toString();
                if(!mail.contains("@")||pass.length()<8){toast(host,"Informe e-mail válido e senha de pelo menos 8 caracteres.");return;}
                if(register){
                    work(host,"Cadastro",()->{cloud.signUp(mail,pass);return "Código de verificação enviado, se o provedor permitir.";},
                        result->verify(host,cloud,mail,scan,tests));
                }else{
                    work(host,"Login",()->{cloud.signIn(mail,pass);return "Conectado com segurança.";},
                        result->open(host,cloud,scan,tests));
                }
            }).setNegativeButton("Cancelar",null).show();
    }
    private static void verify(Activity host,CloudClient cloud,String email,
                               LinkedHashMap<String,LinkedHashMap<String,String>> scan,
                               LinkedHashMap<String,String> tests){
        EditText otp=text(host,"Código recebido por e-mail",InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(host).setTitle("Verificar e-mail").setView(fields(host,otp))
            .setMessage("Digite o código enviado pelo Neon Auth. Após confirmar, entre com seu e-mail e senha.")
            .setPositiveButton("Verificar",(d,w)->work(host,"Verificação",()->{
                cloud.verifyOtp(email,otp.getText().toString().trim());
                return "E-mail verificado. Agora faça login.";
            },done->credentials(host,cloud,false,scan,tests)))
            .setNegativeButton("Cancelar",null).show();
    }
    private static void consent(Activity host,CloudClient cloud,
                                LinkedHashMap<String,LinkedHashMap<String,String>> scan,
                                LinkedHashMap<String,String> tests){
        new AlertDialog.Builder(host).setTitle("Autorizar envio deste diagnóstico?")
            .setMessage("Serão enviados ao seu banco Neon: modelo, versão do Android, leituras de hardware, medições e resultados de testes. Nenhum IMEI, número de série, gravação de áudio ou coordenada de GPS será enviado. Os relatórios PDF permanecem locais. Você pode apagar seus registros depois.")
            .setPositiveButton("Autorizar e enviar",(d,w)->work(host,"Sincronização",
                ()->"Diagnóstico registrado: "+cloud.sync(host.getApplicationContext(),scan,tests),null))
            .setNegativeButton("Não enviar",null).show();
    }
    private static void delete(Activity host,CloudClient cloud){
        new AlertDialog.Builder(host).setTitle("Apagar dados na nuvem?")
            .setMessage("Esta ação apaga os seus aparelhos cadastrados e os diagnósticos, testes e medições associados. Não pode ser desfeita. Não apaga a conta de login.")
            .setPositiveButton("Apagar meus registros",(d,w)->work(host,"Exclusão",
                ()->{cloud.deleteAllMyScans();return "Registros da conta apagados.";},null))
            .setNegativeButton("Cancelar",null).show();
    }
    private static void work(Activity host,String title,Callable<String> task,Consumer<String> after){
        Toast.makeText(host,"Executando "+title.toLowerCase()+"…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                String result=task.call();
                host.runOnUiThread(()->{
                    if(host.isFinishing()||host.isDestroyed())return;
                    new AlertDialog.Builder(host).setTitle(title).setMessage(result)
                        .setPositiveButton("OK",(d,w)->{if(after!=null)after.accept(result);}).show();
                });
            }catch(Exception e){
                host.runOnUiThread(()->{
                    if(!host.isFinishing()&&!host.isDestroyed())
                        new AlertDialog.Builder(host).setTitle("Falha em "+title)
                            .setMessage(e.getMessage()==null?"Erro desconhecido":e.getMessage())
                            .setPositiveButton("OK",null).show();
                });
            }
        },"cpu-z-cloud").start();
    }
    private static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
}
