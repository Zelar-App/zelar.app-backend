package app.zelar.usuario.controller;

import app.zelar.usuario.dto.CriarUsuarioRequest;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import app.zelar.usuario.exception.EmailJaCadastradoException;
import app.zelar.usuario.service.UsuarioService;
import app.zelar.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
@Import(SecurityConfig.class)
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UsuarioService usuarioService;

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
                .andExpect(status().isBadRequest());
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
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value("E-mail já cadastrado"))
                .andExpect(jsonPath("$.caminho").value("/api/v1/usuarios"));
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