package com.delivery.auth.service;

import com.delivery.auth.dto.AuthResponse;
import com.delivery.auth.dto.LoginRequest;
import com.delivery.auth.dto.RegistroRequest;
import com.delivery.auth.model.Usuario;
import com.delivery.auth.repository.UsuarioRepository;
import com.delivery.auth.security.JwtTokenProvider;
import com.delivery.common.enums.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    
    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El email ya está registrado");
        }
        
        Usuario usuario = Usuario.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(request.getNombre())
                .rol(Rol.CLIENTE)
                .activo(true)
                .build();
        
        usuario = usuarioRepository.save(usuario);
        
        String token = tokenProvider.generateToken(usuario.getId(), usuario.getEmail(), usuario.getRol());
        
        return AuthResponse.builder()
                .token(token)
                .userId(usuario.getId())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .build();
    }
    
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));
        
        if (!usuario.getActivo()) {
            throw new RuntimeException("Usuario desactivado");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }
        
        String token = tokenProvider.generateToken(usuario.getId(), usuario.getEmail(), usuario.getRol());
        
        return AuthResponse.builder()
                .token(token)
                .userId(usuario.getId())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .build();
    }
}
