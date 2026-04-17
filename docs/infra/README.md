# Platform / shared infrastructure

Спільна інфраструктура (OIDC/Keycloak, опційно edge) ведеться **в окремому каталозі** поруч із цим репозиторієм — не всередині `quarkus-ms-gold-template`.

| Розташування | Опис |
|--------------|------|
| **`test_q/infra-bootstrap/`** | Канонічний шлях на локальній машині: `/home/ratio/projects/java/test_q/infra-bootstrap` |
| [PLAN.md](../../../infra-bootstrap/PLAN.md) | Покроковий план (фази 0–5), DoD, ризики *(відкривається, якщо обидва каталоги лежать поруч у `test_q/`)* |

Якщо репозиторії клонують у іншу структуру — відкрийте каталог `infra-bootstrap` на тому ж рівні, що й `quarkus-ms-gold-template`, і читайте `README.md` / `PLAN.md` там.
