# Plano de validação

## Compilação
- GitHub Actions compila `:app:assembleDebug` em Java 17 + Android SDK 35.
- Verificar o run mais recente **success** antes de baixar artefato do APK.
- A compilação não testa sensores, aquecimento, câmera, GPS, conectores ou login reais.

## Teste físico no Motorola Edge 50 Pro
1. Instalar APK e confirmar abertura sem internet e sem login.
2. Verificar que bateria, RAM, resolução, versão Android e SoC representam dados reais do aparelho.
3. Abrir **Monitor**: aguardar duas leituras; conferir o gráfico e se fecha sem continuar coletando.
4. Repetir com aparelho frio e depois em uso normal; não forçar temperaturas altas.
5. Realizar os 11 testes guiados e indicar corretamente êxito, falha ou não testado.
6. Salvar histórico local e conferir a comparação sem conexão.
7. Gerar PDF/JSON e verificar se contêm origem dos dados e ressalvas sobre autenticidade.
8. Importar arquivo ADB de exemplo e verificar se campos sensíveis não são importados.
9. Criar conta (código OTP), entrar, autorizar envio e consultar histórico. **Este fluxo depende de teste manual real**.
10. Conferir o isolamento entre contas distintas; nunca copiar senha/credenciais para o GitHub.
11. Verificar exclusão de registros sem afetar autenticação.
12. Comparar dados com especificação oficial Motorola; divergências não devem indicar falsificação.

## Pendências de produto
- Teste físico de todos os componentes e diferentes fabricantes de Android.
- Envio seguro de PDF ao bucket privado; atualmente apenas JSON do diagnóstico é sincronizado.
- Assinatura de lançamento, política de retenção configurável, termos e processo de publicação.
- Conectores OEM de autenticidade, quando o fabricante disponibilizar atestação confiável.
