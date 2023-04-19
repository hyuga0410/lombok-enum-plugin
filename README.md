# lombok-enum-plugin

## Lombok枚举扩展插件

- 增强Lombok枚举扩展支持</li>
- 支持自动生成枚举类中自定义的常量属性get方法</li>
- 支持常规类上使用@EnumDesc
- 支持常规类中的枚举成员变量上使用@EnumDesc
- 不支持枚举类/接口/抽象类/非枚举成员属性等场景使用@EnumDesc
- JDK Version 11

__该插件用于将自定义枚举常量的<code>get</code>方法添加到<code>IntelliJ IDEA</code>的<code>PSI</code>中，以解决自动生成的<code>get</code>方法无法通过<code>IDEA</code>编译的问题。__

> 插件原作者为:[pengqinglong](https://github.com/pengqinglong17199/lombok-pql-plugins)
>
> 本插件在其基础上扩展了class类型注解也支持自定义属性生成get方法，由IDEA2023.1版本开发。