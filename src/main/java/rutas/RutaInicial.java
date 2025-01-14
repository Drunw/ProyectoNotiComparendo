package rutas;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.protocol.HTTP;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RegisterForReflection
@ApplicationScoped
public class RutaInicial extends RouteBuilder {

    @ConfigProperty(name = "cedulasConsultar")
    String cedulas;

    @ConfigProperty(name = "urlConsultar1")
    String url1;

    public static final String ACCOUNT_SID = "AC56ce2f93147d375a1d44f0c62ebb2b4d";
  //  public static final String AUTH_TOKEN = "7f423d5503452a9d2548fa3e935ad380";


    @Override
    public void configure() throws Exception {

        from("direct:rutaConsultaInicial").routeId("ConsultaInicial")
                .setBody(simple(cedulas.toString()))
                .log("SE INICIA LA CONSULTA DE CEDULAS.")
                .split().body()
                .process(exchange -> {
                    String cedulas = (String) exchange.getIn().getBody();
                    String[] partes = cedulas.split("-");
                    exchange.setProperty("cedula", partes[0]);
                    exchange.setProperty("celular", partes[1]);
                })
                .removeHeaders("*")
                .setHeader(Exchange.HTTP_METHOD, simple("GET"))
                .setHeader(Exchange.CONTENT_TYPE, simple("application/json"))
                .setHeader("ocp-apim-subscription-key", simple("e69beca20a9942f381a2e80c072e451b"))
                .setHeader("origin", simple("https://webfenix.movilidadbogota.gov.co"))
                .doTry()
                .toD(url1 + "${exchangeProperty.cedula}?bridgeEndpoint=true")
                .log("RESPUESTA BACK: ${body}")
                .to("direct:envioNoti")
                .doCatch(Exception.class)
                .log("HUBO UN ERROR AL CONSUMIR EL APi: ${exception}")
                .setBody(simple("${exception}"))
                .convertBodyTo(String.class)
                .to("direct:sendEmail")
                .end()
                .end();

       from("direct:envioNoti").routeId("EnvioNotificacion")
               .choice().when(simple("${body} != null"))
               .log("ENVIANDO NOTIFICACION.")
               .convertBodyTo(String.class)
               .unmarshal().json()
               .process(exchange -> {
                   LinkedHashMap<String, Object> data = (LinkedHashMap<String, Object>) exchange.getIn().getBody();

                   // Construir el texto
                   StringBuilder texto = new StringBuilder();
                   @SuppressWarnings("unchecked")
                   List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("value");

                   int contador = 1; // Inicializamos un contador
                   for (Map<String, Object> item : items) {
                       texto.append(contador).append("- ") // Agregamos el contador al texto
                               .append("ID: ").append(item.get("id"))
                               .append(", Tipo: ").append(item.get("tipo"))
                               .append(", Estado: ").append(item.get("estado"))
                               .append(", Saldo: ").append(item.get("saldo"))
                               .append(", Valor Descuento: ").append(item.get("valorDescuento"))
                               .append(", Saldo Pendiente: ").append(item.get("saldoPendiente"))
                               .append(", Intereses: ").append(item.get("interes"))
                               .append(", Total: ").append(item.get("total")).append(".")
                               .append("\\n");
                       contador++; // Incrementamos el contador después de cada iteración
                   }

                   // Imprimir el texto final
                   String mensaje = "Buen dia, se le informa que tiene los siguientes comparendos pendientes: \\n" + texto;

                    String celular = (String) exchange.getProperty("celular");
                    celular = celular.trim();
                    exchange.setProperty("celular",celular);
                    exchange.setProperty("mensaje",mensaje);
               })
               .log("celular: ${exchangeProperty.celular} mensaje: ${exchangeProperty.mensaje}")
               .to("velocity:/request.vm?allowContextMapAll=true&encoding=UTF-8")
               .to("direct:sendWp")
               .otherwise()
               .log("NO SE NOTIFICARA")
                .end();

        from("direct:sendEmail")
                .process(exchange -> {
                    String excepcion = (String) exchange.getIn().getBody();
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String timestamp = now.format(formatter);  // Formatear la fecha y hora
                    String subject = "REVISAR EXCEPCION: " + excepcion + " "+ timestamp;
                    String to = "halodani31.ldra@gmail.com"; // Dirección de correo del destinatario

                    exchange.getIn().setHeader("Subject", subject);
                    exchange.getIn().setHeader("To", to);
                    exchange.getIn().setBody("HUBO UNA EXCEPCIONN EN EL PROCESAMIENTO DE LOS MENSAJES."); // El archivo PDF en el cuerpo del correo
                    exchange.getIn().setHeader("Content-Type", "application/text"); // Tipo de contenido para el archivo adjunto
                })
                .to("smtp://smtp.gmail.com:587?username=halodani31.ldra@gmail.com&password=xlod%20qbiu%20aszz%20fkwr&from=halodani31.ldra@gmail.com&to=halodani31.ldra@gmail.com&subject=ATENCION&mail.smtp.auth=true&mail.smtp.starttls.enable=true");

        from("direct:sendWp").routeId("sendWp")
                .setHeader(Exchange.CONTENT_TYPE,simple("application/json"))
                .setHeader(Exchange.HTTP_METHOD,simple("POST"))
                .doTry()
                .log("json a ser enviado: ${body}")
                .to("https://pruebasmandawp.onrender.com/lead?bridgeEndpoint=true")
                .log("Body enviado correctamente: ${body}")
                .doCatch(Exception.class)
                .log("Ha ocurrido un error al intentar enviar el mensaje: ${exception}")
                .setBody(simple("${exception}"))
                .convertBodyTo(String.class)
                .to("direct:sendEmail")
                .end()
                .end();
    }
}
