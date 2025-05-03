## Features 

- [ ] Auto configure pekko 
  - [ ] Configure guardian actor 
  - [ ] Configure parent actor which users can use to create actors easily 
  - [ ] Configure cluster with ease 
  - [ ] Create sharded actors with ease
- [ ] Production ready features 
  - [ ] Configure cluster dynamically at runtime 
- [ ] Refactor cluster settings 

- register as bean 
  - find that specific actor easily 

## ETC 

- Why use `public Behavior<Command> create()` instead of `public static Behavior<Command> create()` 
  - To integrate with spring DI easily, I chose to implement it non-static 
    - Injecting as beans 
    - Allow inheriting from interfaces such as `SingletonBehavior` 
