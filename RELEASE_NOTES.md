# Release Notes — v1.1.9

<id>
Perbaikan penekanan popup consent (cookie, privacy, Google Funding Choices) agar tidak dianggap iklan. Bypass asset statis CDN untuk mencegah layout rusak. Blokir redirect skrip in-page ke host judol/iklan. Perbaikan video speed yang terjebak di 2x setelah dilepas. Respons blokir iklan kini mengembalikan 403 tanpa CORS header untuk mencegah fallback loader.
</id>

<en-US>
Fixed consent popup false positives (cookie, privacy, Google Funding Choices) so they are no longer treated as ads. Added static CDN asset bypass to prevent layout breakage. Blocked in-page script redirects to ad/judol hosts. Fixed video speed stuck at 2x after releasing hold gesture. Blocked ad responses now return 403 without CORS headers to prevent loader fallback.
</en-US>
