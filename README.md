# CPU-Z Android

Aplicativo nativo Android de diagnóstico geral de hardware, temperatura, bateria e componentes, com relatórios locais e backend Neon **opcional**.

## Identidade visual
- Tema escuro: **preto #000000, vermelho #E60000 e branco**.
- Tema claro: **branco #FFFFFF, vermelho #E60000 e preto**.
- Cinza neutro para textos de apoio e vermelho escuro #8B0000 para variações.
- Logo original do proprietário vetorizada em `res/drawable/logo_mark.xml`, aplicada no cabeçalho e no ícone adaptativo do Android.
- Alterne os temas pelo controle **Claro / Escuro** no cabeçalho; preferência salva localmente.
- Não foram modificados o fluxo de diagnóstico, permissões de testes, banco Neon ou formatos de relatório nesta mudança visual.

## Funcionalidades
- Identificação do dispositivo, Android, processador/SoC, núcleos, RAM, armazenamento, tela e conectividade
- Bateria (temperatura, tensão, corrente, ciclos se fornecidos, carregamento e saúde declarada pelo sistema)
- Sondas extras **somente leitura** em fontes OEM acessíveis de `/sys/class/power_supply` e `/sys/devices/system/cpu`
- Estado térmico, margem térmica (quando disponível), gráficos de temperatura de bateria em tempo real **sem teste de estresse**
- Inventário de câmeras, modos de tela, sensores e dispositivos de áudio
- Testes guiados de tela, multitouch, microfone, alto-falante, vibração, câmera, lanterna, GPS, acelerômetro, botões e conector
- Importação de relatórios ADB de texto **selecionados pelo usuário**, somente com campos permitidos
- Histórico local privado e comparação com a medição anterior
- Exportação **PDF e JSON** pelo seletor de arquivos
- Conta opcional **Neon Auth**, envio manual confirmado, histórico na nuvem, análise térmica via SQL e comparação com fichas técnicas oficiais

## Infraestrutura em produção
- **Neon PostgreSQL 18**: `red-tooth-82407441`, banco `neondb`, branch `main`
- **Managed Better Auth**: login por e-mail e senha, com verificação de código OTP
- **Neon Data API** (PostgREST): acesso JWT com Row-Level Security, proprietário isolado por `auth.user_id()`
- **8 tabelas públicas** e esquema privado de atestações OEM
- Bucket `diagnostic-reports` **privado e reservado**. O APK atual **não faz upload de PDF para o bucket**; envia dados JSON do diagnóstico à Data API somente com autorização explícita.
- Arquivos SQL versionados em `database/` e fonte oficial Motorola Edge 50 Pro incluída

## Proteção de dados
**Offline por padrão.** Nenhuma leitura é enviada automaticamente ao abrir o aplicativo. O usuário decide criar conta e deve confirmar cada envio. O app não solicita IMEI, número de série, contatos, coordenadas GPS para salvar, foto capturada nem gravações de microfone. Senhas, cookies e JWTs são utilizados apenas em memória durante a sessão e não persistidos pelo aplicativo. O relatório local é salvo somente após a seleção de destino. Os dados sincronizados ficam na região AWS `us-east-1` do projeto Neon.

O usuário pode remover os próprios registros na nuvem pela interface. A conta de autenticação não é apagada pela ação de apagar históricos. Veja `docs/PRIVACIDADE_E_LIMITES.md`.

## Limitações obrigatórias
As APIs públicas do Android e os dumps do ADB **não certificam se uma peça é original, substituída ou falsa**. A comparação com especificações oficiais é apenas indicativa; variações de firmware, componentes da mesma linha, unidades de medidas, drivers e configurações podem produzir diferenças sem defeito. Certificação confiável exige evidências de procedência verificadas pelo fabricante ou avaliação técnica.

Temperatura de **bateria** não é temperatura de CPU/GPU. Algumas medições OEM podem estar bloqueadas; nesse caso o app informa `indisponível`. O modo ADB somente importa um texto, não executa comandos nem eleva privilégios. O bucket privado está preparado, mas **backup/upload de PDF ainda não é uma funcionalidade do APK**.

## Compilar
- Java 17 / AGP 8.7.3 / Gradle 8.9 / compileSdk 35 / minSdk 26.
- Na aba **Actions** do GitHub, abra o build **Android APK** mais recente que terminou com sucesso.
- Baixe o artefato `CPU-Z-Android-debug` e instale `app-debug.apk`.
- É um APK de desenvolvimento, não uma versão assinada para publicação na Play Store.

A compilação automatizada valida o código, **não substitui testes de sensores em um dispositivo real**. Consulte `docs/VALIDACAO.md`.

## Organização
- `app/src/main/java/com/conexaostreet/cpuzandroid/`: app, diagnósticos, nuvem e relatórios
- `database/`: schema, políticas e catálogo OEM com fontes
- `docs/`: validação, privacidade e instruções ADB

Copyright: este projeto não é afiliado à CPUID nem à Motorola. A marca CPU-Z pode estar protegida em determinadas regiões; verifique os direitos de uso antes de uma publicação comercial.
