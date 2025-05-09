import com.vanniktech.maven.publish.SonatypeHost

val pekkoVersion = "1.1.3"
val springBootVersion = "2.7.0"

dependencies {
    constraints {
        api("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
        api("org.apache.pekko:pekko-cluster-typed_3:$pekkoVersion")
        api("org.apache.pekko:pekko-cluster-sharding-typed_3:$pekkoVersion")
        api("org.springframework.boot:spring-boot-starter:$springBootVersion")
    }
}
