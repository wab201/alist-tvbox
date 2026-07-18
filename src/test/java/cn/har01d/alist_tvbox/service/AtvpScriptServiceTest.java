package cn.har01d.alist_tvbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AtvpScriptServiceTest {
    @Test
    void renderShouldInjectManagedSelfKeyringChunks() throws Exception {
        SecspiderKeyService secspiderKeyService = mock(SecspiderKeyService.class);
        when(secspiderKeyService.ensureKeyMaterial()).thenReturn(new SecspiderKeyService.KeyStatus(
                true,
                "/data/secspider/self-ed25519-private.pem",
                "/data/secspider/self-ed25519-public.pem",
                "/data/secspider/self-master-secret.txt",
                "/data/secspider/atvp-keyring.json",
                "public-key",
                List.of("pub-a", "pub-b"),
                List.of("secret-a", "secret-b")
        ));

        AtvpScriptService service = new AtvpScriptService(secspiderKeyService, new ObjectMapper());

        String script = service.render();
        String expectedVersionMaterial = org.apache.commons.codec.digest.DigestUtils.sha256Hex(
                Files.readString(Path.of("src/main/resources/static/Atvp.py"), StandardCharsets.UTF_8))
                + "\n"
                + "pub-a|pub-b"
                + "\n"
                + "secret-a|secret-b";

        assertThat(script).contains("    _self_public_key_chunks = [\"pub-a\",\"pub-b\"]");
        assertThat(script).contains("    _self_master_secret_chunks = [\"secret-a\",\"secret-b\"]");
        assertThat(script).doesNotContain("    _self_public_key_chunks = []");
        assertThat(script).doesNotContain("    _self_master_secret_chunks = []");
        assertThat(service.version()).isEqualTo(org.apache.commons.codec.digest.DigestUtils.sha256Hex(expectedVersionMaterial).substring(0, 12));
    }

    @Test
    void atvpShouldForwardPythonSideConfigToInnerSpider() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/Atvp.py"), StandardCharsets.UTF_8);

        assertThat(script).contains("for key in (\"api\", \"token\", \"api_key\", \"apikey\", \"alist_tvbox_api\", \"alist_tvbox_token\", \"alist_tvbox_api_key\")");
        assertThat(script).contains("extras[\"local_proxy_config\"] = proxy_value");
        assertThat(script).doesNotContain("proxy_value = str(payload.get(\"local_proxy_config\") or \"\").strip()");
        assertThat(script).contains("value = payload.get(\"localProxyConfig\")");
        assertThat(script).contains("self._category_mode = self._resolve_category_mode(payload)");
        assertThat(script).contains("def _resolve_category_mode(self, payload):");
    }

    @Test
    void atvpShouldKeepMagnetDetailOnPluginPlaybackPath() throws Exception {
        String script = Files.readString(Path.of("src/main/resources/static/Atvp.py"), StandardCharsets.UTF_8);

        assertThat(script).contains("return self._parse(share_url)");
        assertThat(script).contains("parsed_result = self._sort_detail_play_items(parsed_result)");
        assertThat(script).contains("params={\"id\": str(play_id or \"\"), \"from\": \"jar\", \"type\": \"client-proxy\"}");
        assertThat(script).contains("def _extract_backend_play_id(self, url_value):");
        assertThat(script).contains("Atvp backend /p url converted to client-proxy play");
        assertThat(script).contains("def _restore_original_magnet_id(self, play_id):");
        assertThat(script).contains("source_text = self._sanitize_inner_source(source_text)");
        assertThat(script).contains("return text.lstrip(\"\\ufeff\").replace(\"\\x00\", \"\")");
        assertThat(script).contains("if value.startswith(self.DETAIL_PREFIX):");
        assertThat(script).contains("ids = [value[len(self.DETAIL_PREFIX):]]");
        assertThat(script).contains("def _looks_like_direct_play_id(self, play_id):");
        assertThat(script).contains("return self._build_direct_play_detail(value)");
        assertThat(script).contains("lowered.startswith((\"magnet:\", \"ed2k://\", \"jab-offline:\", \"llss-offline:\", \"llss-pending:\"))");
        assertThat(script).contains("result[\"url\"] = self._restore_original_magnet_id(vid)");
        assertThat(script).contains("result = self._proxy_player_content(result, result.get(\"url\") if isinstance(result, dict) else vid, context)");
        assertThat(script).contains("if share_url is not None and not self._category_mode_enabled():");
        assertThat(script).contains("if payload.get(\"parse\") == 0:");
        assertThat(script).contains("payload.pop(\"parse\", None)");
        assertThat(script).contains("Atvp local proxy player ok");
        assertThat(script).contains("def _with_inferred_local_proxy_type(self, result):");
        assertThat(script).contains("Atvp inferred PAN115 local proxy type for magnet playback");
        assertThat(script).contains("payload[\"type\"] = \"PAN115\"");
    }
}
