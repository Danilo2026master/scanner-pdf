# ∇ Scanabla — escanear, organizar, exportar (Android)

Preto e branco, fonte serifada, direto ao ponto.

## O que faz

- **Escanear**: a câmera acha as bordas, corta, endireita e melhora a imagem. Todas as páginas da sessão viram **um único PDF**, salvo na hora.
  - Na tela do documento, **+ Páginas** continua escaneando **no mesmo PDF**.
  - Voltou ao início e escaneou de novo? É **outro PDF**, salvo separado.
- **Abrir PDF** do celular (ou "Abrir com → Scanabla" em outro app).
- **Organizar páginas**: mover, girar, excluir e adicionar páginas (escaneando, de outro PDF ou de um documento salvo).
- **Unir PDFs**: junta vários em um, já com a ordem que você quiser.
- **Converter para LaTeX** (abas Fotos · Código · PDF):
  - **Organizado**: as anotações viram um documento arrumado: título, seções, listas, caixas de definição, fórmulas em destaque, exemplos, tabelas. Fonte Times.
  - **Fiel**: transcrição exata, na ordem e com as palavras do caderno.
  - Desenhos e gráficos são **recortados da foto** e entram como figuras.
  - Se o LaTeX der erro, o app pede a correção ao Gemini sozinho (até 2 vezes).
  - O PDF aparece na própria tela; toque nas páginas ou fotos para ampliar.
- **Enviar para GitHub**: manda o **projeto LaTeX** (main.tex, figuras/ e o PDF) para editar depois no **Overleaf** ou no **TeXmaker**.
  - Com "Compactar (ZIP)": um .zip (no Overleaf: Novo projeto → Carregar projeto). Em repositório público aparece "Abrir no Overleaf".
  - Sem ZIP: uma pasta com os arquivos, num único commit.
  - PDF escaneado (sem LaTeX) vai como projeto também: um main.tex que inclui as páginas do PDF.
- **Texto (OCR)**, **Enviar** (PDF ou projeto .zip), **Renomear**, **Excluir** e **cópia em Download/Scanabla**.

## O que precisa de configuração

| Recurso | Precisa de |
|---|---|
| Escanear, organizar, unir, OCR | nada (Google Play Services atualizado) |
| LaTeX com fórmulas | chave grátis do Gemini: <https://aistudio.google.com/apikey> |
| LaTeX → PDF | internet (compilação grátis em latex.ytotech.com; reserva: latexonline.cc) |
| GitHub | token pessoal com permissão **Contents: leitura e escrita** (e **Administration** para criar repositório pelo app) |

Sem a chave do Gemini, o LaTeX sai só com o texto simples lido pelo OCR (sem fórmulas).
As chaves ficam salvas só no celular.

## Instalar

Baixe o `scanabla.apk` em **Releases** direto no celular e instale.
O Scanabla instala **ao lado** do antigo "Scanner PDF", que pode ser removido depois.

## Atualizar sem desinstalar (opcional)

Cada build do GitHub Actions usa uma assinatura nova, e o Android recusa atualizar por cima.
Para fixar a assinatura, crie uma chave e guarde-a como segredo `DEBUG_KEYSTORE` do repositório
(Settings → Secrets and variables → Actions):

```
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
base64 -w0 debug.keystore   # cole o resultado no segredo
```

## Fonte

A Times New Roman não pode ser distribuída dentro de apps. O Scanabla usa a **Tinos**, desenhada com as mesmas medidas da Times New Roman (licença SIL OFL 1.1, veja `FONTE-TINOS-LICENCA.txt`).
