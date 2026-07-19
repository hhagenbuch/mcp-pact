package io.github.hhagenbuch.mcppact.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void flattensNestedCapabilitiesToDottedPaths() throws Exception {
        var caps = mapper.readTree("{\"tools\":{},\"resources\":{\"subscribe\":true,\"listChanged\":false}}");
        assertThat(Capabilities.flatten(caps))
                .containsExactlyInAnyOrder("tools", "resources", "resources.subscribe", "resources.listChanged");
    }

    @Test
    void emptyObjectYieldsNoPaths() {
        assertThat(Capabilities.flatten(mapper.createObjectNode())).isEmpty();
    }

    @Test
    void nonObjectYieldsNoPaths() {
        assertThat(Capabilities.flatten(mapper.nullNode())).isEmpty();
        assertThat(Capabilities.flatten(mapper.missingNode())).isEmpty();
    }
}
