# 自用 Docker 镜像自动发布说明

本文档记录 `codex/dual-plugin-loader` 分支的自用 Docker 镜像发布路径。目标是在上游暂未合并 PR 时，仍然可以稳定构建并发布包含自用双 loader / 插件编译入口的 AList-TVBox 镜像。

## 发布入口

新增工作流：

```text
.github/workflows/publish-self-docker.yaml
```

默认发布到 GitHub Container Registry：

```text
ghcr.io/<你的 GitHub 用户名>/alist-tvbox
```

当前 fork 用户名为 `wab201` 时，常用镜像地址是：

```text
ghcr.io/wab201/alist-tvbox:latest-self
ghcr.io/wab201/alist-tvbox:dual-plugin-loader
ghcr.io/wab201/alist-tvbox:sha-<提交短哈希>
```

## 触发方式

### 1. 手动发布

进入 GitHub 仓库页面：

```text
Actions -> Publish self Docker image -> Run workflow
```

可填写：

```text
image_tag: dual-plugin-loader
publish_latest_self: true
```

发布后会生成：

```text
ghcr.io/wab201/alist-tvbox:dual-plugin-loader
ghcr.io/wab201/alist-tvbox:latest-self
ghcr.io/wab201/alist-tvbox:sha-<提交短哈希>
```

### 2. 推送分支自动发布

当 `codex/dual-plugin-loader` 分支有新的 push 时，会自动构建并发布：

```text
ghcr.io/wab201/alist-tvbox:dual-plugin-loader
ghcr.io/wab201/alist-tvbox:latest-self
ghcr.io/wab201/alist-tvbox:sha-<提交短哈希>
```

### 3. 标签发布

推送 `self-v*` 格式的 tag 时，会按 tag 名发布：

```powershell
git tag self-v2026.07.17
git push origin self-v2026.07.17
```

生成镜像：

```text
ghcr.io/wab201/alist-tvbox:self-v2026.07.17
ghcr.io/wab201/alist-tvbox:sha-<提交短哈希>
```

tag 发布不会自动覆盖 `latest-self`，适合保留可回退版本。

## 构建链路

工作流执行的实际链路如下：

1. 拉取仓库代码。
2. 使用 Node.js 24 安装 WebUI 依赖。
3. 执行 `npm run build` 构建前端。
4. 使用 GraalVM JDK 25 构建后端。
5. 执行 Maven 测试和打包：

```bash
mvn -B -Dtest=*,!PostgreSqlMigrationTest -DfailIfNoTests=false -DforkCount=0 package --file pom.xml
```

`PostgreSqlMigrationTest` 依赖 Docker/Testcontainers，在普通发布链路里排除，避免因为外部测试环境导致镜像发布被阻断。

6. 执行 Spring Boot layertools：

```bash
cd target
java -Djarmode=layertools -jar alist-tvbox-1.0.jar extract
```

7. 写入 `data/version` 和 `data/app_version`。
8. 使用 `docker/Dockerfile` 构建本地 amd64 测试镜像。
9. 启动容器并检查：

```text
http://localhost:4567/api/alist/status
```

只有接口返回 `2` 时，才继续发布正式镜像。

10. 发布多架构镜像：

```text
linux/amd64
linux/arm64
```

## 使用镜像

服务器上拉取镜像：

```bash
docker pull ghcr.io/wab201/alist-tvbox:latest-self
```

基础运行示例：

```bash
docker rm -f alist-tvbox 2>/dev/null || true
docker run -d \
  --name alist-tvbox \
  -p 4567:4567 \
  -p 5244:5244 \
  -e ALIST_PORT=5244 \
  -e INSTALL=new \
  -v /opt/alist-tvbox:/data \
  -v /opt/alist-tvbox/www-static:/www/static \
  -v /opt/alist-tvbox/alist:/opt/alist/data \
  ghcr.io/wab201/alist-tvbox:latest-self
```

访问：

```text
管理界面: http://服务器IP:4567/
AList: http://服务器IP:5244/
```

## GHCR 权限设置

工作流使用 GitHub 自带的 `GITHUB_TOKEN` 发布 GHCR 镜像，不需要 Docker Hub 密钥。

仓库需要允许 Actions 写入 package：

```text
Settings -> Actions -> General -> Workflow permissions
```

建议设置：

```text
Read and write permissions
```

如果镜像是 private，服务器拉取时需要登录：

```bash
echo <GitHub Personal Access Token> | docker login ghcr.io -u wab201 --password-stdin
```

如果希望免登录拉取，可以在 GitHub 的 package 页面把镜像可见性改为 public。

## 回退方法

每次发布都会附带提交哈希标签：

```text
ghcr.io/wab201/alist-tvbox:sha-<提交短哈希>
```

如果 `latest-self` 有问题，可以直接回退到上一版 hash 标签：

```bash
docker pull ghcr.io/wab201/alist-tvbox:sha-上一版提交短哈希
docker rm -f alist-tvbox
docker run -d ... ghcr.io/wab201/alist-tvbox:sha-上一版提交短哈希
```

正式自用里程碑建议额外打 `self-v*` tag：

```powershell
git tag self-v2026.07.17
git push origin self-v2026.07.17
```

这样可以长期保留一个语义化回退点。

## 与原项目发布流程的区别

原项目已有：

```text
.github/workflows/release.yaml
.github/workflows/build-dev.yaml
```

这些流程主要面向上游正式发布或 Docker Hub 发布，需要配置：

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
```

自用工作流的区别：

1. 发布到 GHCR，不依赖 Docker Hub。
2. 默认绑定 `codex/dual-plugin-loader` 分支。
3. 发布前会真实启动容器检查 `/api/alist/status`。
4. 保留 `latest-self`、分支标签、提交哈希标签三类入口，方便自动更新和版本回退。
5. 不改变上游 release 流程，不影响正式 PR。

## 排障记录

### Actions 没有推送镜像权限

检查：

```text
Settings -> Actions -> General -> Workflow permissions
```

确认启用：

```text
Read and write permissions
```

### docker pull 提示 unauthorized

说明 GHCR package 仍是 private。

可选方案：

1. 把 package 设置为 public。
2. 服务器使用 GitHub token 登录 GHCR。

### 容器 smoke test 失败

先看 Actions 里的 `Smoke test local image` 日志。该步骤会打印容器日志：

```bash
docker logs self-alist-tvbox-test
```

常见原因：

1. 后端启动失败。
2. `data/` 资源缺失。
3. Docker 基础镜像变更。
4. `/api/alist/status` 在 60 秒内没有进入 `2`。

### 需要发布 Docker Hub

如果以后仍想发布到 Docker Hub，可以复用原项目的 `build-dev.yaml`，只需配置：

```text
DOCKERHUB_USERNAME
DOCKERHUB_TOKEN
```

然后手动运行 `release dev docker` 工作流，指定：

```text
branch: codex/dual-plugin-loader
tag: dual-plugin-loader
```

不过自用分支建议优先走 GHCR，少一层账号密钥维护。
