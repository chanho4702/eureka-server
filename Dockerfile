# 런타임 전용. jar는 CI(또는 로컬)의 `gradlew bootJar` 산출물(build/libs/app.jar)을 복사한다.
# 컨테이너 안에서 gradle 빌드하지 않는 이유: CI runner의 gradle 캐시 재사용 + CRLF gradlew 이슈 회피.
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY build/libs/app.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
