# Como usar a importação ADB (opcional)
A coleta com ADB é manual e requer depuração USB autorizada pelo usuário. Não há root nem execução de comandos privilegiados pelo aplicativo.

Em um computador que já tenha ADB configurado e o celular devidamente autorizado:

```bash
adb shell dumpsys battery > battery.txt
adb shell dumpsys thermalservice > thermal.txt
adb shell getprop > properties.txt
```

Combine o conteúdo dos três arquivos em **um .txt** e copie para o celular. No CPU-Z Android, use **Importar ADB** e selecione o arquivo. O parser só extrai um conjunto restrito de campos (bateria, estado de boot, SoC e algumas temperaturas) e não importa IMEI, serial e outros campos identificadores conhecidos.

**Importante:** o arquivo ADB pode conter informações privadas. Revise-o antes de compartilhar. O texto é fornecido pelo usuário e não prova peças originais, tampouco substitui serviço técnico.
