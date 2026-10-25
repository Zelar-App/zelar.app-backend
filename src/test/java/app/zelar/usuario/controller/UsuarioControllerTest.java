package app.zelar.usuario.controller;

import app.zelar.autenticacao.security.SecurityExceptionHandler;
import app.zelar.config.SecurityConfig;
import app.zelar.usuario.dto.CriarUsuarioRequest;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.exception.EmailJaCadastradoException;
import app.zelar.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, SecurityExceptionHandler.class})
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void deveRetornar201AoCadastrarUsuario() throws Exception {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "joao@email.com",
                "Senha123",
                null
        );

        Usuario usuario = new Usuario(
                "João Silva",
                "joao@email.com",
                "senha-criptografada",
                PerfilUsuario.CIDADAO,
                null
        );

        when(usuarioService.criar(any(CriarUsuarioRequest.class)))
                .thenReturn(usuario);

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nome").value("João Silva"))
                .andExpect(jsonPath("$.email").value("joao@email.com"))
                .andExpect(jsonPath("$.perfil").value("CIDADAO"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void deveRetornar400QuandoDadosForemInvalidos() throws Exception {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "",
                "email-invalido",
                "123",
                null
        );

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Bad Request"))
                .andExpect(jsonPath("$.mensagem", containsString("nome:")))
                .andExpect(jsonPath("$.mensagem", containsString("email:")))
                .andExpect(jsonPath("$.mensagem", containsString("senha:")))
                .andExpect(jsonPath("$.caminho").value("/api/v1/usuarios"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void deveRetornar400QuandoJsonEstiverMalformado() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(
                        "Corpo da requisição ausente ou inválido. Envie um JSON válido."
                ))
                .andExpect(jsonPath("$.caminho").value("/api/v1/usuarios"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void deveRetornar409QuandoEmailJaEstiverCadastrado() throws Exception {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "joao@email.com",
                "Senha123",
                null
        );

        when(usuarioService.criar(any(CriarUsuarioRequest.class)))
                .thenThrow(new EmailJaCadastradoException());

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.erro").value("Conflict"))
                .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"))
                .andExpect(jsonPath("$.caminho").value("/api/v1/usuarios"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
    @Test
    void deveRetornarTelefoneQuandoInformado() throws Exception {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "joao@email.com",
                "Senha123",
                "31999999999"
        );

        Usuario usuario = new Usuario(
                "João Silva",
                "joao@email.com",
                "senha-criptografada",
                PerfilUsuario.CIDADAO,
                "31999999999"
        );

        when(usuarioService.criar(any(CriarUsuarioRequest.class)))
                .thenReturn(usuario);

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.telefone").value("31999999999"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }
    @Test
    void deveRetornar400QuandoTelefoneExcederLimite() throws Exception {
        CriarUsuarioRequest request = new CriarUsuarioRequest(
                "João Silva",
                "joao@email.com",
                "Senha123",
                "1".repeat(21)
        );

        mockMvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(usuarioService);
    }
}
