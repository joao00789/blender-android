extends Node3D
## Modelador 3D mobile - Fase 1: viewport, primitivas, transformações, cor e exportação GLB.

const OrbitCamera = preload("res://scripts/orbit_camera.gd")

const PRIMITIVES := ["Cubo", "Esfera", "Cilindro", "Cone", "Plano", "Toro"]
const MOVE_MODES := ["XZ", "Y", "Tela"]
const HINT := "1 dedo: orbitar  |  2 dedos: zoom e mover câmera  |  toque no objeto: selecionar"

var cam_rig: OrbitCamera
var objects_root: Node3D
var selected: MeshInstance3D = null
var counter := 0

var current_tool := "select"
var move_mode := "XZ"
var drag_mode := ""
var pending_deselect := false
var drag_moved := 0.0
var drag_plane := Plane()
var drag_offset := Vector3.ZERO
var touch_points := {}
var last_pinch_dist := 0.0
var last_pinch_mid := Vector2.ZERO

var pos_fields: Array[SpinBox] = []
var rot_fields: Array[SpinBox] = []
var scl_fields: Array[SpinBox] = []
var color_btn: ColorPickerButton
var name_label: Label
var status_label: Label
var move_mode_btn: Button
var updating_ui := false


func _ready() -> void:
    OS.request_permissions()
    _build_world()
    _build_ui()
    add_primitive("Cubo")
    _say(HINT)


# ---------------------------------------------------------------- mundo 3D

func _build_world() -> void:
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color(0.23, 0.23, 0.24)
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color(0.7, 0.7, 0.75)
    env.ambient_light_energy = 0.6
    var we := WorldEnvironment.new()
    we.environment = env
    add_child(we)

    var sun := DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-50, -30, 0)
    add_child(sun)

    cam_rig = OrbitCamera.new()
    add_child(cam_rig)

    objects_root = Node3D.new()
    objects_root.name = "Objetos"
    add_child(objects_root)

    add_child(_make_grid())


func _make_grid() -> MeshInstance3D:
    var im := ImmediateMesh.new()
    im.surface_begin(Mesh.PRIMITIVE_LINES)
    for i in range(-10, 11):
        var c := Color(0.62, 0.62, 0.65) if i == 0 else Color(0.42, 0.42, 0.44)
        im.surface_set_color(c)
        im.surface_add_vertex(Vector3(i, 0, -10))
        im.surface_set_color(c)
        im.surface_add_vertex(Vector3(i, 0, 10))
        im.surface_set_color(c)
        im.surface_add_vertex(Vector3(-10, 0, i))
        im.surface_set_color(c)
        im.surface_add_vertex(Vector3(10, 0, i))
    im.surface_end()
    var mat := StandardMaterial3D.new()
    mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    mat.vertex_color_use_as_albedo = true
    var mi := MeshInstance3D.new()
    mi.mesh = im
    mi.material_override = mat
    return mi


# ---------------------------------------------------------------- objetos

func add_primitive(kind: String) -> void:
    var mesh: Mesh
    match kind:
        "Cubo":
            mesh = BoxMesh.new()
        "Esfera":
            mesh = SphereMesh.new()
        "Cilindro":
            mesh = CylinderMesh.new()
        "Cone":
            var cone := CylinderMesh.new()
            cone.top_radius = 0.0
            mesh = cone
        "Plano":
            mesh = PlaneMesh.new()
        "Toro":
            mesh = TorusMesh.new()
        _:
            return
    var mi := MeshInstance3D.new()
    counter += 1
    mi.name = "%s_%d" % [kind, counter]
    mi.mesh = mesh
    var mat := StandardMaterial3D.new()
    mat.albedo_color = Color(0.8, 0.8, 0.8)
    mi.material_override = mat
    objects_root.add_child(mi)
    _add_pick_body(mi)
    select(mi)


func _add_pick_body(mi: MeshInstance3D) -> void:
    mi.create_trimesh_collision()
    for child in mi.get_children():
        if child is StaticBody3D:
            for n in child.get_children():
                if n is CollisionShape3D and n.shape is ConcavePolygonShape3D:
                    n.shape.backface_collision = true


func select(mi: MeshInstance3D) -> void:
    if is_instance_valid(selected):
        _set_highlight(selected, false)
    selected = mi
    if is_instance_valid(selected):
        _set_highlight(selected, true)
    _refresh_props()


func _set_highlight(mi: MeshInstance3D, on: bool) -> void:
    var mat := mi.material_override as StandardMaterial3D
    if mat:
        mat.emission_enabled = on
        mat.emission = Color(0.12, 0.22, 0.45)


func _pick(screen_pos: Vector2) -> MeshInstance3D:
    var cam := cam_rig.camera
    var from := cam.project_ray_origin(screen_pos)
    var to := from + cam.project_ray_normal(screen_pos) * 1000.0
    var query := PhysicsRayQueryParameters3D.create(from, to)
    var hit := get_world_3d().direct_space_state.intersect_ray(query)
    if hit.is_empty():
        return null
    var collider: Node = hit["collider"]
    return collider.get_parent() as MeshInstance3D


func _duplicate_selected() -> void:
    if not is_instance_valid(selected):
        return
    var copy := selected.duplicate() as MeshInstance3D
    counter += 1
    copy.name = "Copia_%d" % counter
    copy.material_override = (selected.material_override as StandardMaterial3D).duplicate()
    objects_root.add_child(copy)
    copy.position += Vector3(0.5, 0.0, 0.5)
    select(copy)


func _delete_selected() -> void:
    if not is_instance_valid(selected):
        return
    var s := selected
    select(null)
    s.queue_free()


func _export_glb() -> void:
    var copy := objects_root.duplicate()
    for obj in copy.get_children():
        for c in obj.get_children():
            if c is StaticBody3D:
                obj.remove_child(c)
                c.free()
    var doc := GLTFDocument.new()
    var state := GLTFState.new()
    var err := doc.append_from_scene(copy, state)
    if err != OK:
        copy.free()
        _say("Erro ao preparar a exportação (código %d)" % err)
        return
    var dirs := [OS.get_system_dir(OS.SYSTEM_DIR_DOWNLOADS), "user://"]
    for d in dirs:
        var path: String = String(d).path_join("modelo_%d.glb" % int(Time.get_unix_time_from_system()))
        if doc.write_to_filesystem(state, path) == OK:
            copy.free()
            _say("Exportado: " + path)
            return
    copy.free()
    _say("Não foi possível salvar o arquivo GLB")


# ---------------------------------------------------------------- entrada

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        var st := event as InputEventScreenTouch
        if st.pressed:
            touch_points[st.index] = st.position
            if touch_points.size() == 2:
                _end_drag()
                _reset_pinch()
            elif touch_points.size() == 1:
                # Touch input does not automatically become mouse input on
                # Android. Start the same interaction explicitly so one
                # finger can select, orbit, move, rotate, or scale.
                _begin_drag(st.position)
        else:
            touch_points.erase(st.index)
            if touch_points.is_empty():
                _finish_drag()
    elif event is InputEventScreenDrag:
        var sd := event as InputEventScreenDrag
        touch_points[sd.index] = sd.position
        if touch_points.size() == 2:
            _two_finger_gesture()
        elif touch_points.size() == 1:
            _on_touch_motion(sd)
    elif event is InputEventMouseButton:
        _on_mouse_button(event as InputEventMouseButton)
    elif event is InputEventMouseMotion:
        _on_mouse_motion(event as InputEventMouseMotion)


func _reset_pinch() -> void:
    var pts := touch_points.values()
    if pts.size() >= 2:
        last_pinch_dist = (pts[0] - pts[1]).length()
        last_pinch_mid = (pts[0] + pts[1]) * 0.5


func _two_finger_gesture() -> void:
    var pts := touch_points.values()
    var d: float = (pts[0] - pts[1]).length()
    var mid: Vector2 = (pts[0] + pts[1]) * 0.5
    if last_pinch_dist > 1.0 and d > 1.0:
        cam_rig.zoom(last_pinch_dist / d)
    cam_rig.pan(mid - last_pinch_mid)
    last_pinch_dist = d
    last_pinch_mid = mid


func _on_mouse_button(e: InputEventMouseButton) -> void:
    if e.button_index == MOUSE_BUTTON_WHEEL_UP and e.pressed:
        cam_rig.zoom(0.9)
    elif e.button_index == MOUSE_BUTTON_WHEEL_DOWN and e.pressed:
        cam_rig.zoom(1.1)
    elif e.button_index == MOUSE_BUTTON_LEFT:
        if e.pressed:
            if touch_points.size() < 2:
                _begin_drag(e.position)
        else:
            _finish_drag()
    elif e.button_index == MOUSE_BUTTON_RIGHT or e.button_index == MOUSE_BUTTON_MIDDLE:
        drag_mode = "pan" if e.pressed else ""


func _on_mouse_motion(e: InputEventMouseMotion) -> void:
    if touch_points.size() >= 2:
        return
    match drag_mode:
        "orbit":
            drag_moved += e.relative.length()
            cam_rig.orbit(e.relative)
        "pan":
            cam_rig.pan(e.relative)
        "transform":
            drag_moved += e.relative.length()
            _apply_tool(e)


func _begin_drag(pos: Vector2) -> void:
    drag_moved = 0.0
    pending_deselect = false
    var hit := _pick(pos)
    if hit != null:
        select(hit)
        if current_tool == "select":
            drag_mode = "orbit"
        else:
            if current_tool == "move":
                _begin_move(pos)
            drag_mode = "transform"
    else:
        drag_mode = "orbit"
        pending_deselect = true


func _finish_drag() -> void:
    if drag_mode == "orbit" and pending_deselect and drag_moved < 10.0:
        select(null)
    _end_drag()


func _on_touch_motion(e: InputEventScreenDrag) -> void:
    if drag_mode == "orbit":
        drag_moved += e.relative.length()
        cam_rig.orbit(e.relative)
    elif drag_mode == "transform":
        drag_moved += e.relative.length()
        _apply_tool(e)


func _end_drag() -> void:
    drag_mode = ""
    pending_deselect = false


# ---------------------------------------------------------------- ferramentas

func _begin_move(pos: Vector2) -> void:
    var cam := cam_rig.camera
    var origin := selected.global_position
    var normal := -cam.global_transform.basis.z
    if move_mode == "XZ":
        normal = Vector3.UP
    elif move_mode == "Y":
        normal.y = 0.0
        if normal.length() < 0.001:
            normal = Vector3.FORWARD
        normal = normal.normalized()
    drag_plane = Plane(normal, origin)
    var hit: Variant = drag_plane.intersects_ray(cam.project_ray_origin(pos), cam.project_ray_normal(pos))
    if hit == null:
        drag_offset = Vector3.ZERO
    else:
        drag_offset = origin - (hit as Vector3)


func _drag_move(pos: Vector2) -> void:
    var cam := cam_rig.camera
    var hit: Variant = drag_plane.intersects_ray(cam.project_ray_origin(pos), cam.project_ray_normal(pos))
    if hit == null:
        return
    var np: Vector3 = (hit as Vector3) + drag_offset
    if move_mode == "Y":
        var p := selected.global_position
        p.y = np.y
        selected.global_position = p
    else:
        selected.global_position = np


func _apply_tool(e: InputEventMouseMotion) -> void:
    if not is_instance_valid(selected):
        return
    match current_tool:
        "move":
            _drag_move(e.position)
        "rotate":
            var cam_x := cam_rig.camera.global_transform.basis.x.normalized()
            var q := Quaternion(Vector3.UP, e.relative.x * 0.01) * selected.quaternion
            q = Quaternion(cam_x, e.relative.y * 0.01) * q
            selected.quaternion = q.normalized()
        "scale":
            var f := 1.0 - e.relative.y * 0.005
            selected.scale = (selected.scale * f).clamp(Vector3.ONE * 0.05, Vector3.ONE * 50.0)
    _refresh_props()


func _cycle_move_mode() -> void:
    var i := (MOVE_MODES.find(move_mode) + 1) % MOVE_MODES.size()
    move_mode = MOVE_MODES[i]
    move_mode_btn.text = "Eixo: " + move_mode


# ---------------------------------------------------------------- interface

func _say(text: String) -> void:
    status_label.text = text


func _btn_style(c: Color) -> StyleBoxFlat:
    var sb := StyleBoxFlat.new()
    sb.bg_color = c
    sb.set_corner_radius_all(6)
    sb.content_margin_left = 14
    sb.content_margin_right = 14
    sb.content_margin_top = 8
    sb.content_margin_bottom = 8
    return sb


func _make_theme() -> Theme:
    var th := Theme.new()
    th.default_font_size = 22
    var panel := StyleBoxFlat.new()
    panel.bg_color = Color(0.16, 0.16, 0.17, 0.96)
    th.set_stylebox("panel", "PanelContainer", panel)
    th.set_stylebox("normal", "Button", _btn_style(Color(0.27, 0.27, 0.28)))
    th.set_stylebox("hover", "Button", _btn_style(Color(0.33, 0.33, 0.34)))
    th.set_stylebox("pressed", "Button", _btn_style(Color(0.28, 0.45, 0.70)))
    th.set_stylebox("hover_pressed", "Button", _btn_style(Color(0.32, 0.50, 0.75)))
    th.set_stylebox("focus", "Button", StyleBoxEmpty.new())
    return th


func _make_button(text: String, width: float, cb: Callable) -> Button:
    var b := Button.new()
    b.text = text
    b.custom_minimum_size = Vector2(width, 56)
    b.pressed.connect(cb)
    return b


func _make_spin(minv: float, maxv: float, step: float) -> SpinBox:
    var sb := SpinBox.new()
    sb.min_value = minv
    sb.max_value = maxv
    sb.step = step
    sb.custom_minimum_size = Vector2(110, 48)
    sb.select_all_on_focus = true
    sb.value_changed.connect(_on_field_changed)
    return sb


func _add_row(parent: Container, title: String, fields: Array[SpinBox], minv: float, maxv: float, step: float) -> void:
    var l := Label.new()
    l.text = title
    parent.add_child(l)
    var h := HBoxContainer.new()
    for i in 3:
        var sb := _make_spin(minv, maxv, step)
        fields.append(sb)
        h.add_child(sb)
    parent.add_child(h)


func _build_ui() -> void:
    var layer := CanvasLayer.new()
    add_child(layer)
    var root := Control.new()
    root.theme = _make_theme()
    root.mouse_filter = Control.MOUSE_FILTER_IGNORE
    layer.add_child(root)
    root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

    var vbox := VBoxContainer.new()
    vbox.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(vbox)
    vbox.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

    # barra superior
    var top := PanelContainer.new()
    vbox.add_child(top)
    var bar := HBoxContainer.new()
    top.add_child(bar)

    var add_btn := MenuButton.new()
    add_btn.text = "+ Adicionar"
    add_btn.custom_minimum_size = Vector2(150, 56)
    var popup := add_btn.get_popup()
    popup.add_theme_constant_override("v_separation", 14)
    for k in PRIMITIVES:
        popup.add_item(k)
    popup.id_pressed.connect(func(id: int) -> void: add_primitive(PRIMITIVES[id]))
    bar.add_child(add_btn)

    var group := ButtonGroup.new()
    for t in [["select", "Sel."], ["move", "Mover"], ["rotate", "Girar"], ["scale", "Escalar"]]:
        var key: String = t[0]
        var b := Button.new()
        b.text = t[1]
        b.toggle_mode = true
        b.button_group = group
        b.custom_minimum_size = Vector2(100, 56)
        b.button_pressed = (key == current_tool)
        b.pressed.connect(func() -> void: current_tool = key)
        bar.add_child(b)

    move_mode_btn = _make_button("Eixo: " + move_mode, 130, _cycle_move_mode)
    bar.add_child(move_mode_btn)

    var spacer := Control.new()
    spacer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    spacer.mouse_filter = Control.MOUSE_FILTER_IGNORE
    bar.add_child(spacer)

    bar.add_child(_make_button("Duplicar", 120, _duplicate_selected))
    bar.add_child(_make_button("Excluir", 110, _delete_selected))
    bar.add_child(_make_button("GLB", 80, _export_glb))

    # corpo com painel de propriedades à direita
    var body := HBoxContainer.new()
    body.size_flags_vertical = Control.SIZE_EXPAND_FILL
    body.mouse_filter = Control.MOUSE_FILTER_IGNORE
    vbox.add_child(body)

    var sp := Control.new()
    sp.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    sp.mouse_filter = Control.MOUSE_FILTER_IGNORE
    body.add_child(sp)

    var panel := PanelContainer.new()
    panel.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
    body.add_child(panel)
    var margin := MarginContainer.new()
    for side in ["margin_left", "margin_right", "margin_top", "margin_bottom"]:
        margin.add_theme_constant_override(side, 12)
    panel.add_child(margin)
    var props := VBoxContainer.new()
    margin.add_child(props)

    name_label = Label.new()
    name_label.text = "Nada selecionado"
    props.add_child(name_label)
    _add_row(props, "Posição", pos_fields, -1000.0, 1000.0, 0.01)
    _add_row(props, "Rotação (graus)", rot_fields, -3600.0, 3600.0, 1.0)
    _add_row(props, "Escala", scl_fields, 0.01, 1000.0, 0.01)

    color_btn = ColorPickerButton.new()
    color_btn.text = "Cor"
    color_btn.custom_minimum_size = Vector2(0, 56)
    color_btn.color_changed.connect(_on_color_changed)
    props.add_child(color_btn)

    # barra de status
    status_label = Label.new()
    status_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    status_label.custom_minimum_size = Vector2(0, 36)
    status_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    vbox.add_child(status_label)


func _refresh_props() -> void:
    updating_ui = true
    var has := is_instance_valid(selected)
    name_label.text = String(selected.name) if has else "Nada selecionado"
    color_btn.disabled = not has
    var p := selected.position if has else Vector3.ZERO
    var r := selected.rotation_degrees if has else Vector3.ZERO
    var s := selected.scale if has else Vector3.ONE
    for i in 3:
        pos_fields[i].editable = has
        rot_fields[i].editable = has
        scl_fields[i].editable = has
        pos_fields[i].set_value_no_signal(p[i])
        rot_fields[i].set_value_no_signal(r[i])
        scl_fields[i].set_value_no_signal(s[i])
    if has:
        var mat := selected.material_override as StandardMaterial3D
        if mat:
            color_btn.color = mat.albedo_color
    updating_ui = false


func _on_field_changed(_v: float) -> void:
    if updating_ui or not is_instance_valid(selected):
        return
    selected.position = Vector3(pos_fields[0].value, pos_fields[1].value, pos_fields[2].value)
    selected.rotation_degrees = Vector3(rot_fields[0].value, rot_fields[1].value, rot_fields[2].value)
    selected.scale = Vector3(scl_fields[0].value, scl_fields[1].value, scl_fields[2].value)


func _on_color_changed(c: Color) -> void:
    if updating_ui or not is_instance_valid(selected):
        return
    var mat := selected.material_override as StandardMaterial3D
    if mat:
        mat.albedo_color = c
