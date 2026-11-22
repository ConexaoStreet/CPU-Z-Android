# CPU-Z Android

Aplicativo nativo Android de diagnóstico geral de dispositivos, com coleta local de informações de hardware, testes guiados e relatórios PDF/JSON.

## Recursos implementados
- Identificação de aparelho, Android, atualização de segurança, CPU/SoC e ABI
- Bateria: nível, temperatura, saúde informada pelo sistema, tensão, corrente e ciclos (se disponibilizados)
- Estado térmico do Android, RAM, armazenamento e tela
- Câmeras reconhecidas, sensores e seus fabricantes declarados, conectividade e recursos
- Testes guiados de tela, multitouch, alto-falante, vibração, microfone, câmera, flash, GPS, acelerômetro e botões
- Relatórios PDF e JSON exportados localmente pelo seletor de documentos do Android
- Situação dos componentes marcada como **não verificável** quando não há atestado confiável

## Limites importantes
**O Android não oferece uma API pública universal que comprove se tela, bateria, câmeras ou outras peças são originais.** Modelo, fabricante declarado, presença de sensor, número de câmeras e funcionamento não demonstram autenticidade ou ausência de troca. O app **nunca** afirma 'original' ou 'falsificada' sem uma evidência verificável do fabricante.

A saúde e os ciclos da bateria podem não estar disponíveis em alguns aparelhos. Temperatura da bateria não é temperatura do processador; estado térmico é informado pelo Android. Testes guiados podem depender da confirmação do usuário. Este app não faz medições clínicas nem certificação de componentes.

## Privacidade
Não usa rede, não possui backend nem coleta identificação sensível (IMEI, número de série ou contatos). Áudio do teste de microfone é amostrado em memória e não é salvo. Foto do teste de câmera permanece no app de câmera. Os relatórios são criados apenas no local escolhido pelo usuário.

## Compilar e instalar
Projeto Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, compileSdk 35, minSdk 26. Abra no Android Studio ou use a aba **Actions** deste repositório para baixar o artefato de APK `CPU-Z-Android-debug` depois de um build bem-sucedido. APK de debug é para teste e não equivale a uma versão assinada de produção.

## Estado
Versão inicial funcional: exige compilação/validação em um Android real antes de considerar os testes homologados.
