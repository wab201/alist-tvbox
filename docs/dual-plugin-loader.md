# 双插件 Loader 说明

本文记录 `codex/dual-plugin-loader` 分支的插件加载策略，目标是在同一个 AList-TVBox fork 中同时验证：

- 原版 `har01d5/tvbox` 仓库的 stock `secspider/1` 插件。
- 自有私钥和 master secret 打包的自用 `secspider/1` 插件。

## 实现点

### 1. 订阅显式下发 loader

插件站点生成时，`ext` 内会包含：

```json
{
  "loader": "ATV_ADDRESS/Atvp.py",
  "api": "ATV_ADDRESS",
  "source": "ATV_ADDRESS/plugins/{token}/{id}.txt",
  "token": "...",
  "secret": "...",
  "local_proxy_config": {}
}
```

这样 `csp_PyProxy + spring.jar` 不再依赖客户端默认 loader 行为，始终加载服务端当前版本的 `Atvp.py`。

### 2. `Atvp.py` 支持双 keyring

`Atvp.py` 默认保留 stock keyring：

- `_public_key_chunks`
- `_master_secret_chunks`

并新增自有 keyring 占位：

- `_self_public_key_chunks`
- `_self_master_secret_chunks`

解密 `secspider/1` 时默认按顺序尝试：

1. `stock`
2. `self`

其中 `self` 只有在两个自有 chunk 列表都非空时才启用。

## 自有 keyring 填充方式

自有插件不能复用 stock 私钥；正确路径是：

1. 生成自己的 Ed25519 keypair。
2. 生成自己的 master secret。
3. 用自己的私钥和 master secret 打包自用插件。
4. 把自己的 public key 和 master secret 按 `Atvp.py` 现有混淆方式生成 chunks。
5. 填入：

```python
_self_public_key_chunks = [
    "...",
]
_self_master_secret_chunks = [
    "...",
]
```

注意：不要把私钥写进 `Atvp.py`。运行时只需要公钥和 master secret；私钥只用于离线打包签名。

## 插件管理入口编译

管理页路径：

1. 打开 `订阅管理`。
2. 点击 `订阅源管理`。
3. 点击 `三方插件编译`。

编译入口用于把自有 Python 明文插件打包成 `secspider/1` 文本包，适合放入自己的插件仓库。表单字段含义：

- `插件名称`：外层包头 `//@name`，例如 `JavBus`。
- `插件版本`：外层包头 `//@version`，必须大于 0。
- `插件 ID`：外层包头 `//@id`，建议使用稳定值，例如 `javbus_self`。
- `kid`：用于 HKDF salt 和排查 keyring，建议带日期或用途，例如 `self-20260716`。
- `插件明文`：合法 Python 源码，不要包含 `//@name`、`//@format` 等外层包头。
- `Ed25519 私钥`：只随本次请求发送给后端签名，接口不保存、不回显。
- `Ed25519 公钥`：可选；填写后会返回可直接复制到 `_self_public_key_chunks` 的 chunks。
- `master secret`：自有 `Atvp.py` 运行时解包需要的 secret，会返回 `_self_master_secret_chunks`。

明文插件最小示例：

```python
from base.spider import Spider

class Spider(Spider):
    def getName(self):
        return "Demo"

    def playerContent(self, flag, id, vipFlags):
        return {"parse": 0, "url": id}
```

编译后，把 `插件包` 内容保存到自有插件仓库，例如：

```text
repo/
  spiders_v2.json
  py/
    javbus_self.txt
```

`spiders_v2.json` 示例：

```json
[
  {
    "id": "javbus_self",
    "file": "py/javbus_self.txt",
    "version": 1,
    "valid": true
  }
]
```

如果要让某个站点强制只走自有 keyring，可在插件扩展配置中写：

```json
{
  "secspider_loader": "self"
}
```

如果要并行验证原版插件和自有插件，保持默认 `auto` 即可：stock 包会由 stock keyring 解密，自有包会在 stock 失败后转到 self keyring。

## 可选强制 loader

默认 `secspider_loader=auto`，即 stock 和 self 都尝试。

如果某个插件需要强制选择，可以在插件扩展数据中加入：

```json
{
  "secspider_loader": "self"
}
```

支持值：

- `auto`：默认，依次尝试 stock/self。
- `dual`：同 `auto`。
- `stock`：只尝试 stock。
- `self`：只尝试 self；如果自有 keyring 未填充，会回退为可用 keyring。

## 验证路径

### 原版插件

1. 导入 `https://github.com/har01d5/tvbox`。
2. 生成订阅。
3. 解码站点 `ext`，确认存在 `loader`、`source`、`local_proxy_config`。
4. 在客户端播放原版插件。
5. 预期由 stock keyring 解密成功。

### 自有插件

1. 填入自有 keyring。
2. 用自有私钥/master secret 打包插件。
3. 导入自有插件仓库。
4. 生成订阅并播放。
5. 预期 stock keyring 失败后，self keyring 解密成功，并输出：

```text
Atvp secspider loader selected: self
```

## 回退

如果自有插件验证失败：

1. 清空 `_self_public_key_chunks` 和 `_self_master_secret_chunks`。
2. 删除插件扩展里的 `secspider_loader`。
3. 保留 `loader=ATV_ADDRESS/Atvp.py`，它对 stock 插件是正向修复。
4. 重新生成订阅，验证原版插件仍可播放。

## 安全边界

- 不绕过 Ed25519 签名校验。
- 不绕过 SHA256 hash 校验。
- 不修改已签名插件正文。
- 不把私钥提交到仓库。
