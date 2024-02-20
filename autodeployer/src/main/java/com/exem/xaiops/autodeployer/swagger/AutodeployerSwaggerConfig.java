package com.exem.xaiops.autodeployer.swagger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.*;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.time.LocalDate;

@Configuration
@EnableSwagger2
public class AutodeployerSwaggerConfig {
    @Value("${logpresso.source.host}")
    private String sourceHost;
    @Value("${logpresso.source.port}")
    private String sourcePort;
    @Value("${logpresso.target.host}")
    private String targetHost;
    @Value("${logpresso.target.port}")
    private String targetPort;
    @Value("${logpresso.source.system_id}")
    private String sourceSystemId;
    @Value("${logpresso.target.system_id}")
    private String targetSystemId;
    @Bean
    public Docket eDesignApi() {
        return new Docket(DocumentationType.SWAGGER_2).apiInfo(this.apiInfo()).enable(true).select().apis(RequestHandlerSelectors.basePackage("com.exem.xaiops.autodeployer"))
                .paths(PathSelectors.any()).build().pathMapping("/").directModelSubstitute(LocalDate.class, String.class)
                .genericModelSubstitutes(ResponseEntity.class).useDefaultResponseMessages(true)
                .enableUrlTemplating(false).useDefaultResponseMessages(false);
    }
    @Bean
    UiConfiguration uiConfig() {
        return UiConfigurationBuilder.builder()
                .deepLinking(true)
                .displayOperationId(false)
                .defaultModelsExpandDepth(-1)
                .defaultModelExpandDepth(10)
                .defaultModelRendering(ModelRendering.MODEL)
                .displayRequestDuration(true)
                .filter(false).maxDisplayedTags(0)
                .operationsSorter(OperationsSorter.ALPHA)
                .docExpansion(DocExpansion.LIST).showExtensions(false).tagsSorter(TagsSorter.ALPHA)
                .supportedSubmitMethods(UiConfiguration.Constants.DEFAULT_SUBMIT_METHODS)
                .validatorUrl(null)
                .build();
    }
    private ApiInfo apiInfo() {
        final String description = String.format("XAIOps 로그프레소 AutoDeployer(수집 배포 자동화) API에 대한 연동 문서입니다.\n\n<b>Source</b> 로그프레소 서버 (system_id : %S)　　[%s:%s]　<a href='http://%s:%s' target='_blank'>바로가기</a>\n<b>Target</b> 로그프레소 서버 (system_id : %S)　　[%s:%s]　<a href='http://%s:%s' target='_blank'>바로가기</a>"
                , sourceSystemId, sourceHost, sourcePort, sourceHost, sourcePort, targetSystemId, targetHost, targetPort, targetHost, targetPort);
        return new ApiInfoBuilder().title("XAIOps Api Documentation").description(description)
                .version("1.1").termsOfServiceUrl("https://www.ex-em.com/").licenseUrl("http://www.apache.org/licenses/LICENSE-2.0").build();
    }
}
