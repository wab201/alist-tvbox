package cn.har01d.alist_tvbox.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyServiceTest {
    @Test
    void parsePlayUrlIdShouldAcceptIsoSuffix() {
        assertThat(ProxyService.parsePlayUrlId("1@106306.iso")).isEqualTo(106306);
    }

    @Test
    void pan115DriversShouldUseDirectProxy() {
        assertThat(ProxyService.isPan115Driver("115 Cloud")).isTrue();
        assertThat(ProxyService.isPan115Driver("115 Share")).isTrue();
        assertThat(ProxyService.isPan115Driver("115 Index")).isTrue();
        assertThat(ProxyService.isPan115Driver("AliyunShare")).isFalse();
    }

    @Test
    void hopByHopHeadersShouldNotBeForwarded() {
        assertThat(ProxyService.isHopByHopRequestHeader("Host")).isTrue();
        assertThat(ProxyService.isHopByHopRequestHeader("accept-encoding")).isTrue();
        assertThat(ProxyService.isHopByHopRequestHeader("Range")).isFalse();
        assertThat(ProxyService.isHopByHopRequestHeader("User-Agent")).isFalse();
    }
}
