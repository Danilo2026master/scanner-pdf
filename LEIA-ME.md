# ∇ Scanabla — escanear, organizar, exportar (Android)

Preto e branco, fonte serifada, direto ao ponto.

## O que faz

- **Escanear**: a câmera acha as bordas, corta, endireita e melhora a imagem. Todas as páginas da sessão viram **um único PDF**, salvo na hora.
  - Na tela do documento, **+ Páginas** continua escaneando **no mesmo PDF**.
  - Voltou ao início e escaneou de novo? É **outro PDF**, salvo separado.
- **Abrir PDF** do celular (ou "Abrir com → Scanabla" em outro app).
- **Organizar páginas**: mover, girar, excluir e adicionar páginas (escaneando, de outro PDF ou de um documento salvo).
- **Unir PDFs**: junta vários em um, já com a ordem que você quiser.
- **Converter para LaTeX**: fotos de escrita à mão viram código LaTeX editável e depois PDF. O `.tex` fica salvo junto.
- **Enviar para GitHub**: manda o PDF ou um **ZIP** (PDF + `.tex`) para um repositório seu.
- **Texto (OCR)**, **Enviar**, **Renomear**, **Excluir** e **cópia em Download/Scanabla**.

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
