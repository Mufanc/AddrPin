## 食用方法

* 启用模块，选择推荐的作用域，重启

* 重启后 IP 地址默认为 192.168.137.1

* 支持动态修改 IP 地址（需重新开启热点）

```shell
setprop debug.apin.ipaddr 192.168.43.1/24 
```

从 [0980484](https://github.com/Mufanc/AddrPin/commit/09804842fd84f86bafa7fe9dc3222771876ea9ce) 起，只支持 Android 14。如果需要在旧的 Android 版本使用，建议签出到 [7240805](https://github.com/Mufanc/AddrPin/commit/7240805915c3b0652ce5b472b7abd8a19e77d71a) 或更早的提交
