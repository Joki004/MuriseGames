# Integrating with your unpushed local project

Your existing local files were not available for comparison. This starter was prepared separately
from the remote `main`, which contained only a README. Your machine's files have not been modified.

Safest review: clone the repository into a **new folder**, then check out the starter branch there.
Compare its `api/` and `web/murise-games/` with your original local project before adopting changes.

If you prefer to merge into your existing checkout, first commit your own work to a local branch,
after reviewing staged files and excluding secrets/generated output. Fetch the starter branch and
resolve any add/add conflicts deliberately. Do not use a hard reset or blanket overwrite.

Both generated projects may define the same application entry point and frontend pages. Keep only
one Spring Boot application class after resolving package differences. Preserve any existing local
`AGENTS.md` instructions; this starter does not replace them. The CI folder is `.github/workflows/`
(plural), not `.github/workflow/`.
