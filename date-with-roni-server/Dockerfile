# ---------- שלב בנייה ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /src

# קודם רק קבצי הבנייה. כל עוד הם לא משתנים, דוקר משתמש בשכבה
# המטמונת של התלויות ולא מוריד אותן מחדש בכל build.
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
# תהליך אחד במקום דמון קומפילציה נפרד — פחות תקורה ל-build
# חד-פעמי בתוך קונטיינר.
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx2g -Dkotlin.compiler.execution.strategy=in-process"

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null

COPY src src
RUN ./gradlew --no-daemon installDist

# ---------- שלב הרצה ----------
FROM eclipse-temurin:21-jre

# לא רצים כ-root.
RUN useradd --system --uid 1001 --create-home app
USER app

WORKDIR /app
COPY --from=build --chown=app:app /src/build/install/date-with-roni-server/ ./

ENV HOST=0.0.0.0 \
    PORT=8080

EXPOSE 8080

ENTRYPOINT ["/app/bin/date-with-roni-server"]
