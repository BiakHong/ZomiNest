package community.zomi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
class JsonConfiguration {
  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictJson() {
    return b ->
        b.featuresToEnable(
            JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
