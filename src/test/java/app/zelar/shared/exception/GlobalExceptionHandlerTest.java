package app.zelar.shared.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ControllerDeTeste())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deveRetornar404QuandoRecursoNaoExistir() throws Exception {
        mockMvc.perform(get("/teste/recurso"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro").value("Not Found"))
                .andExpect(jsonPath("$.mensagem").value(
                        "Recurso não encontrado."
                ))
                .andExpect(jsonPath("$.caminho").value("/teste/recurso"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @RestController
    static class ControllerDeTeste {

        @GetMapping("/teste/recurso")
        public void consultar() {
            throw new RecursoNaoEncontradoException(
                    "Recurso não encontrado."
            );
        }
    }
}