# Revisão do Ninivia

Origem: diegomazzavolt/Ninivia, branch main, commit 84c0cde690d506b36b8bdb31d85244ddcf0a4884. Código e configurações originais foram lidos antes da reestruturação.

## Problemas encontrados

- Interface e metadados ainda nomeavam o aplicativo como Secure Notes.
- Não existiam estados, fluxo de esclarecimento, ações, prazos ou revisão GTD.
- A sincronização era somente uma caixa de diálogo; Firebase e bibliotecas de rede estavam presentes sem uso.
- Faltavam scripts e JAR do Gradle Wrapper. A assinatura debug exigia um arquivo ausente.
- O teste de screenshot chamava Greeting, função inexistente. Outro teste esperava nome incorreto.
- Salvamento de nota e substituição de anexos não eram transacionais.
- Edição recriava createdAt. Exclusão deixava anexos órfãos.
- O editor recolhia atualizações do banco continuamente, podendo sobrescrever edição em andamento.
- Anexos guardavam URIs sem permissão persistente e não podiam ser abertos pela interface.
- Falha de descriptografia virava o texto “Decryption Error”, que podia substituir conteúdo real num salvamento.
- Backup automático estava habilitado sem política para a chave local do Keystore.
- Não havia distinção entre carregamento, lista vazia e erro, nem tratamento de falhas de escrita.

## Implementação

O banco e a chave original foram preservados. A migração adiciona campos com valores padrão, sem apagar dados. Categorias passam a representar projetos, mantendo seus IDs e associações.

As gravações compostas usam transações Room. Tarefas recorrentes usam identificador determinístico para a próxima ocorrência. O editor carrega uma vez, mantém estado de edição, salva após pausa curta e tenta concluir a gravação antes de voltar. Falhas ficam visíveis, com opção de tentar novamente ou sair descartando alterações.

Notas que não puderem ser decifradas ficam bloqueadas para edição, preservando o original. A exportação é interrompida se houver conteúdo ilegível, em vez de criar silenciosamente um backup incompleto.

Projetos apresentam resultado esperado, progresso e ausência de próxima ação. A lista Hoje contém somente ações ou acompanhamentos com prazo até o dia local. Contextos e pesquisa filtram a lista atual.

O produto funciona localmente; o botão de sincronização fictícia foi substituído por exportação/importação real. Anexos externos ficam explicitamente fora do backup.

## Referências técnicas

- [Migrações Room](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Backup Android](https://developer.android.com/identity/data/autobackup)
- [Acesso persistente a documentos](https://developer.android.com/training/data-storage/shared/documents-files)
- [AGP 9.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes)

## Limites de validação

Resultados efetivos de compilação, testes e análise são registrados em VALIDACAO.md ao final da execução. Instalação no aparelho do proprietário, assinatura de produção e publicação precisam ser tratadas separadamente da compilação local.
