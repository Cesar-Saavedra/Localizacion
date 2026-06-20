package cl.duoc.ms_localizacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// @EnableFeignClients activa el escaneo de interfaces @FeignClient en este paquete
@SpringBootApplication
@EnableFeignClients
public class MsLocalizacionApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsLocalizacionApplication.class, args);
	}

}
