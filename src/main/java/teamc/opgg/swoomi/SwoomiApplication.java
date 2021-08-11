package teamc.opgg.swoomi;

import com.merakianalytics.orianna.Orianna;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SwoomiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwoomiApplication.class, args);
    }
}
