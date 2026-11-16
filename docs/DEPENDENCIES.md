# Dependency decisions
Spring Boot 3.5.16 with Java 21 and its managed Spring Security, Spring Session, Hibernate, PostgreSQL driver and Flyway versions. The official requirements support Java 21: https://docs.spring.io/spring-boot/3.5/system-requirements.html
React 19, TypeScript 5.9 and Vite 7; exact frontend resolutions are committed in package-lock.json. Vite requires Node 20.19+ or 22.12+; Node 24 was used: https://vite.dev/guide/
PostgreSQL 16.14 was used for integration tests. Compose targets PostgreSQL 16 with persistent storage. Development-only test tooling includes JUnit/Spring Test, Vitest and Playwright. No paid API or analytics dependency.
