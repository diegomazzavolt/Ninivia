# Validação da versão 0.2.0

Execução local concluída em 11/09/2026, com Gradle 9.3.1, JDK 21 do Android Studio e SDK Android 36.

## Resultado

Comando: `scripts/build.ps1` → `testDebugUnitTest lintDebug assembleDebug`.

- **BUILD SUCCESSFUL**.
- **22 testes aprovados**, sem falhas e sem testes ignorados.
- Android Lint: **0 erros**, 23 avisos de atualização de dependências e melhorias de estilo.
- APK de desenvolvimento assinado gerado: **17.137.057 bytes**.
- SHA-256: `a787fd50b6948f89f7dcc3a4f2eb451678985fbf59e8b7ff250f27c10b1a1992`.
- `git diff --check` sem erros.

| Conjunto | Testes |
|---|---:|
| Captura, edição, salvamento, busca, reabertura e filtro Hoje | 2 |
| Backup criptografado: round-trip, senha incorreta, adulteração e validação | 5 |
| Recursos Android | 1 |
| Regras GTD, prioridades, datas e recorrência | 6 |
| Migração v1 → v2 com validação de esquema Room | 1 |
| Repositório: preservação, lixeira, importação e transações | 7 |

## Verificação visual

Capturas reais da interface Compose foram geradas por Robolectric/Roborazzi (API 35, configuração 411 × 891 dp) e inspecionadas: Entrada, Hoje e editor. Os textos, controles, cartões e navegação estão legíveis nesse formato.

As imagens estão em `artifacts/screenshots/` após os testes e as capturas da entrega estão em `docs/screenshots/`.

## Limites

- Os testes de interface usam o Android simulado do Robolectric; não houve teste no celular físico do proprietário.
- Os testes instrumentados do Keystore foram incluídos para rodar em aparelho/emulador, mas não foram executados nesta validação.
- O APK entregue usa assinatura de desenvolvimento. Não representa publicação na Play Store nem assinatura de produção.
- O workflow GitHub Actions está preparado; sua execução depende do envio ao repositório.
