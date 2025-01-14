package rutas;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

@ApplicationScoped
@RegisterForReflection
public class ConsumerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        restConfiguration()
                .bindingMode(RestBindingMode.auto);

        from("timer://myTimer?period=900000")  // 30000 ms = 30 segundos
                .to("direct:rutaConsultaInicial2");

        from("timer://myTimer?period=28800000")  // 30000 ms = 30 segundos
                .to("direct:rutaConsultaInicial");
    }
}
