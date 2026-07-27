# Contributing to Prioritize

Thanks for your interest in Prioritize! This guide covers how to build, test and
propose changes. By participating you agree to abide by our
[Code of Conduct](CODE_OF_CONDUCT.md).

## Prerequisites

- **JDK 21 or newer** (the build targets Java 21 via `--release 21`; a newer JDK such as 26 works too).
- **Maven** — either the bundled wrapper (`./mvnw`, `mvnw.cmd`) or a global `mvn`.

## Build & run

```bash
# Build
./mvnw clean package        # or: mvn clean package

# Run (default profile is self-contained H2 — no external services needed)
./mvnw spring-boot:run      # or: mvn spring-boot:run
```

The app starts on port **8080**, REST base path **`/api/v1`**, with a seeded dev admin
`admin` / `p@ssword` (HTTP Basic). Interactive API docs (Swagger UI) are served while the
app runs. See the [README](README.md) for the full profile matrix (`h2`, `postgres`,
`keycloak`, `mqtt`) and the Vaadin admin GUI.

## Tests

```bash
mvn -o test                 # -o = offline, once dependencies are cached
```

**Keep the suite green** — please run the tests before opening a pull request. Most tests
are plain unit tests, but some are `@SpringBootTest` integration tests that expect a
**PostgreSQL** instance (the `postgres` profile — see `application-postgres.yaml`). If you
change behaviour, add or update tests accordingly.

## Branch model & workflow

- Development happens on **`develop`**; **`main`** always points at the latest release tag.
- Create a **feature branch** off `develop` (`feature/<short-name>`), and open a **pull
  request into `develop`**.
- Small, focused changes are easier to review than large ones. For major changes, please
  **open an issue first** to discuss the direction.

## Commit messages

- Use **[Conventional Commits](https://www.conventionalcommits.org/)** — e.g.
  `feat(scheduling): …`, `fix(resource): …`, `docs(readme): …`, `chore(release): …`.
- When a change is pair-programmed or AI-assisted, credit collaborators with a
  `Co-Authored-By:` trailer.
- Before committing, run `git status` and make sure **no new source files are left
  untracked** — a commit that references new files without adding them won't compile.

## Code style

- **Code comments and Javadoc are written in English**, regardless of discussion language.
- **Match the surrounding code** — naming, formatting and comment density of the file you edit.
- New source files carry the **Apache-2.0 license header** used across the codebase.

## API stability

From **1.0.0** onward, the REST API under **`/api/v1`** is the stable, semantically-versioned
contract. Changes that would break it need to be discussed first and belong in a new major
version. The Vaadin `@Route` URLs are an implementation detail and are **not** part of the
contract. See the "API stability" section in the [README](README.md#api-stability).

## Reporting bugs & requesting features

Use the **issue templates** (Bug report / Feature request). For security issues, do **not**
open a public issue — follow the [Security Policy](SECURITY.md) instead.
