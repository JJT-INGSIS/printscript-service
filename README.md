# PrintScript Service

Servicio HTTP de lenguaje de Snippet Searcher. Por ahora contiene únicamente el arranque con Kotlin y Spring Boot; todavía no integra la librería PrintScript ni define contratos HTTP.

Usa `jjt.spring-service:0.1.0` de `gradle-conventions`, JDK 21 y el wrapper Gradle 9.3.0.

```powershell
.\gradlew.bat check
.\gradlew.bat bootRun
```

`check` ejecuta tests, ktlint y detekt, y genera el reporte JaCoCo. Gradle resuelve la convención publicada en GitHub Packages mediante `GITHUB_ACTOR` y `GITHUB_TOKEN` con acceso de lectura.
