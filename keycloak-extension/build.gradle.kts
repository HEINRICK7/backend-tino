plugins { id("tino.java-conventions") }

dependencies {
    compileOnly("org.keycloak:keycloak-server-spi:26.3.5")
    compileOnly("org.keycloak:keycloak-server-spi-private:26.3.5")
    compileOnly("org.keycloak:keycloak-core:26.3.5")
    compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
}
