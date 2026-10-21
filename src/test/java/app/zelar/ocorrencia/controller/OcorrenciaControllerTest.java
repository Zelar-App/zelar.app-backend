package app.zelar.ocorrencia.controller;

import app.zelar.autenticacao.security.SecurityExceptionHandler;
import app.zelar.config.SecurityConfig;
import app.zelar.ocorrencia.dto.CriarOcorrenciaRequest;
import app.zelar.ocorrencia.entity.CategoriaOcorrencia;
import app.zelar.ocorrencia.entity.Localizacao;
import app.zelar.ocorrencia.entity.Ocorrencia;
import app.zelar.ocorrencia.service.OcorrenciaService;
import app.zelar.usuario.entity.PerfilUsuario;
import app.zelar.usuario.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OcorrenciaController.class)
@Import({SecurityConfig.class, SecurityExceptionHandler.class})
class OcorrenciaControllerTest {

    private static final String BODY_VALIDO = """
            {"categoria":"BURACO","descricao":"Buraco na via","latitude":-12.97,"longitude":-38.50}
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OcorrenciaService ocorrenciaService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void deveRetornar201ComUsuarioObtidoDoPrincipal() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID ocorrenciaId = UUID.randomUUID();
        Usuario usuario = new Usuario("João", "joao@email.com", "hash", PerfilUsuario.CIDADAO, null);
        ReflectionTestUtils.setField(usuario, "id", usuarioId);
        Ocorrencia ocorrencia = new Ocorrencia(usuario, CategoriaOcorrencia.BURACO,
                "Buraco na via", new Localizacao(-12.97, -38.50, null));
        ReflectionTestUtils.setField(ocorrencia, "id", ocorrenciaId);
        when(ocorrenciaService.criar(eq(usuarioId), any(CriarOcorrenciaRequest.class))).thenReturn(ocorrencia);

        mockMvc.perform(post("/api/v1/ocorrencias").with(jwt().jwt(token -> token.subject(usuarioId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ocorrenciaId.toString()))
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.status").value("ENVIADA"))
                .andExpect(jsonPath("$.usuario").doesNotExist());

        verify(ocorrenciaService).criar(usuarioId, new CriarOcorrenciaRequest(
                CategoriaOcorrencia.BURACO, "Buraco na via", -12.97, -38.50, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"categoria\":\"DESCONHECIDA\"}",
            "{\"categoria\":\"BURACO\",\"descricao\":\" \",\"latitude\":0,\"longitude\":0}"})
    void deveRetornar400SemChamarServicoQuandoCorpoForInvalido(String body) throws Exception {
        mockMvc.perform(post("/api/v1/ocorrencias")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.caminho").value("/api/v1/ocorrencias"));

        verifyNoInteractions(ocorrenciaService);
    }

    @Test
    void deveRetornar401SemChamarServicoQuandoNaoHouverAutenticacao() throws Exception {
        mockMvc.perform(post("/api/v1/ocorrencias").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ocorrenciaService);
    }

    @Test
    void deveRetornar401QuandoUsuarioDoTokenNaoExistirMais() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(ocorrenciaService.criar(eq(usuarioId), any(CriarOcorrenciaRequest.class)))
                .thenThrow(new BadCredentialsException("Usuário não autenticado."));

        mockMvc.perform(post("/api/v1/ocorrencias").with(jwt().jwt(token -> token.subject(usuarioId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
