## 食用方法

* 启用模块，作用域勾选系统框架，重启

* 重启后 IP 地址默认为 192.168.137.1

* 支持动态修改 IP 地址（需重新开启热点）

```shell
am broadcast -a mufanc.tools.aphelper.IP_ADDRESS -e ipaddr 192.168.43.1/24 
```
