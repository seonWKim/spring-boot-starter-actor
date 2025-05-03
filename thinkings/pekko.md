# Pekko Configuration

- All configuration is held within the instances of `ActorSystem`
    - This means roughly that the default is to parse all application.conf, application.json and
      application.properties found at the root of the class path—please refer to the aforementioned
      documentation for details. The actor system then merges in all reference.conf resources found at the root
      of the class path to form the fallback configuration
- As there are a lot of configuration parameters(and also will be added in the future), I don't want to hard
  code each of every parameter for my library. 
- I want the users to set parameters in a spring-boot like fashion  
```yaml
actor:
  pekko: 
    ... 
```

- As we might integrate other actor systems(such as Akka) in the future, let's add some prefix like `pekko` and `akka`
