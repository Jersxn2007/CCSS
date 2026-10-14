package com.cibertec.skyflow.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cibertec.skyflow.entity.MiembroProyecto;
import com.cibertec.skyflow.entity.Proyecto;
import com.cibertec.skyflow.entity.RolProyecto;
import com.cibertec.skyflow.entity.Usuario;
import com.cibertec.skyflow.service.ProyectoService;
import com.cibertec.skyflow.repository.MiembroProyectoRepository;
import com.cibertec.skyflow.repository.ProyectoRepository;
import com.cibertec.skyflow.repository.UsuarioRepository;

@Service
public class ProyectoServiceImpl implements ProyectoService {

	private final ProyectoRepository proyectoRepository;
	private final MiembroProyectoRepository miembroRepository;
	private final UsuarioRepository usuarioRepository;

	public ProyectoServiceImpl(ProyectoRepository proyectoRepository,
			MiembroProyectoRepository miembroRepository,
			UsuarioRepository usuarioRepository) {
		this.proyectoRepository = proyectoRepository;
		this.miembroRepository = miembroRepository;
		this.usuarioRepository = usuarioRepository;
	}

	/** Crea el proyecto y registra al creador como LIDER (una sola transaccion). */
	@Transactional
	@Override
	public Proyecto crearProyecto(Proyecto proyecto, Long creadorId) {
		Usuario creador = usuarioRepository.findById(creadorId)
				.orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
		if (proyecto.getFechaFin().isBefore(proyecto.getFechaInicio()))
			throw new IllegalArgumentException("La fecha fin no puede ser anterior a la fecha inicio.");

		Proyecto guardado = proyectoRepository.save(proyecto);
		miembroRepository.save(new MiembroProyecto(guardado, creador, RolProyecto.LIDER));
		return guardado;
	}

	/** Solo el LIDER puede agregar miembros; entran como SUBORDINADO. */
	@Transactional
	@Override
	public MiembroProyecto agregarMiembro(Long proyectoId, Long liderId, String correoNuevoMiembro) {
		exigirLider(proyectoId, liderId);

		Usuario nuevo = usuarioRepository.findByCorreo(correoNuevoMiembro)
				.orElseThrow(() -> new IllegalArgumentException("No existe un usuario con ese correo."));
		if (miembroRepository.existsByProyectoIdAndUsuarioId(proyectoId, nuevo.getId()))
			throw new IllegalArgumentException("El usuario ya pertenece al proyecto.");

		Proyecto proyecto = proyectoRepository.getReferenceById(proyectoId);
		return miembroRepository.save(new MiembroProyecto(proyecto, nuevo, RolProyecto.SUBORDINADO));
	}

	@Transactional(readOnly = true)
	@Override
	public List<Proyecto> listarPorUsuario(Long usuarioId) {
		return proyectoRepository.buscarPorUsuario(usuarioId);
	}

	/** Detalle del proyecto; solo para miembros. */
	@Transactional(readOnly = true)
	@Override
	public Proyecto buscarPorId(Long proyectoId, Long usuarioId) {
		exigirMiembro(proyectoId, usuarioId);
		return proyectoRepository.findById(proyectoId)
				.orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
	}

	@Transactional(readOnly = true)
	@Override
	public List<MiembroProyecto> listarMiembros(Long proyectoId, Long usuarioId) {
		exigirMiembro(proyectoId, usuarioId);
		return miembroRepository.findByProyectoId(proyectoId);
	}

	private void exigirMiembro(Long proyectoId, Long usuarioId) {
		if (!miembroRepository.existsByProyectoIdAndUsuarioId(proyectoId, usuarioId))
			throw new IllegalStateException("No perteneces a este proyecto.");
	}

	private void exigirLider(Long proyectoId, Long usuarioId) {
		MiembroProyecto m = miembroRepository.findByProyectoIdAndUsuarioId(proyectoId, usuarioId)
				.orElseThrow(() -> new IllegalStateException("No perteneces a este proyecto."));
		if (m.getRol() != RolProyecto.LIDER)
			throw new IllegalStateException("Solo el lider puede realizar esta accion.");
	}
}
