extends Node3D
## Câmera orbital: orbitar, mover (pan) e zoom ao redor de um ponto alvo.

var camera: Camera3D
var yaw := deg_to_rad(35.0)
var pitch := deg_to_rad(-30.0)
var distance := 8.0
var target := Vector3.ZERO


func _ready() -> void:
    camera = Camera3D.new()
    camera.current = true
    add_child(camera)
    _update()


func orbit(rel: Vector2) -> void:
    yaw -= rel.x * 0.008
    pitch = clampf(pitch - rel.y * 0.008, deg_to_rad(-89.0), deg_to_rad(89.0))
    _update()


func pan(rel: Vector2) -> void:
    var b := camera.global_transform.basis
    var k := distance * 0.0016
    target += (-b.x * rel.x + b.y * rel.y) * k
    _update()


func zoom(factor: float) -> void:
    distance = clampf(distance * factor, 0.5, 200.0)
    _update()


func _update() -> void:
    var dir := Vector3(cos(pitch) * sin(yaw), sin(-pitch), cos(pitch) * cos(yaw))
    look_at_from_position(target + dir * distance, target, Vector3.UP)
