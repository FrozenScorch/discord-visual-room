import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { SceneGraph, FurnitureNode, UserNode, RendererConfig } from './types';
import { FurnitureFactory } from './meshes/FurnitureFactory';
import { UserAvatar } from './meshes/UserAvatar';
import { toThreeVector3, loadTexture } from './utils/sceneUtils';

/**
 * SceneRenderer - Cozy ambient 3D scene for Discord visualization
 *
 * Designed as a "cute little window into the life of the server" for side-monitor use.
 * Warm lighting, gentle auto-orbit, particle ambiance, clean aesthetic.
 */
export class SceneRenderer {
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private renderer!: THREE.WebGLRenderer;
  private controls!: OrbitControls;

  // Scene objects tracked by ID
  private furnitureObjects: Map<string, THREE.Object3D> = new Map();
  private userAvatars: Map<string, UserAvatar> = new Map();
  private avatarTextures: Map<string, THREE.Texture> = new Map();

  // Room visualization
  private roomMesh: THREE.Object3D | null = null;

  // Ambient particles
  private particles: THREE.Points | null = null;

  // Animation
  private animationId: number | null = null;
  private clock: THREE.Clock = new THREE.Clock();
  private elapsedTime: number = 0;

  // Auto-orbit
  private autoOrbitEnabled: boolean = true;
  private autoOrbitSpeed: number = 0.08;
  private lastInteractionTime: number = 0;
  private autoOrbitResumeDelay: number = 8; // seconds of inactivity before auto-orbit resumes

  // Configuration
  private config: RendererConfig;
  private container: HTMLElement;
  private boundHandleResize: () => void;

  constructor(container: HTMLElement, config: Partial<RendererConfig> = {}) {
    this.container = container;

    this.config = {
      wsUrl: config.wsUrl || 'ws://localhost:8080',
      cameraFov: config.cameraFov || 50,
      cameraNear: config.cameraNear || 0.1,
      cameraFar: config.cameraFar || 200,
      lerpFactor: config.lerpFactor || 0.08,
    };

    this.initScene();
    this.initCamera();
    this.initRenderer();
    this.initControls();
    this.initLights();
    this.initRoom();
    this.initParticles();

    FurnitureFactory.initialize();
    this.startRenderLoop();

    this.boundHandleResize = this.handleResize.bind(this);
    window.addEventListener('resize', this.boundHandleResize);

    // Dismiss loading overlay
    const loadingOverlay = document.getElementById('loading-overlay');
    if (loadingOverlay) {
      loadingOverlay.classList.add('loaded');
      document.body.classList.add('scene-loaded');
    }
  }

  /**
   * Update scene with new SceneGraph from backend
   */
  public updateScene(sceneGraph: SceneGraph): void {
    this.updateRoom(sceneGraph.room);
    this.updateFurniture(sceneGraph.furniture);
    this.updateUsers(sceneGraph.users);
  }

  public getScene(): THREE.Scene {
    return this.scene;
  }

  public dispose(): void {
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
      this.animationId = null;
    }

    this.userAvatars.forEach((avatar) => avatar.dispose());
    this.userAvatars.clear();

    this.avatarTextures.forEach((texture) => texture.dispose());
    this.avatarTextures.clear();

    this.furnitureObjects.forEach((obj) => {
      obj.traverse((child) => {
        if (child instanceof THREE.Mesh) {
          child.geometry.dispose();
          if (child.material instanceof THREE.Material) {
            child.material.dispose();
          }
        }
      });
    });
    this.furnitureObjects.clear();

    if (this.particles) {
      this.particles.geometry.dispose();
      (this.particles.material as THREE.Material).dispose();
    }

    this.controls.dispose();
    this.renderer.dispose();
    window.removeEventListener('resize', this.boundHandleResize);
  }

  private initScene(): void {
    this.scene = new THREE.Scene();
    // Soft dark blue-purple gradient feel
    this.scene.background = new THREE.Color(0x161625);
    this.scene.fog = new THREE.FogExp2(0x161625, 0.018);
  }

  private initCamera(): void {
    const aspect = this.container.clientWidth / this.container.clientHeight;
    this.camera = new THREE.PerspectiveCamera(
      this.config.cameraFov, aspect, this.config.cameraNear, this.config.cameraFar
    );
    this.camera.position.set(10, 8, 10);
    this.camera.lookAt(0, 0, 0);
  }

  private initRenderer(): void {
    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
    });
    this.renderer.setSize(this.container.clientWidth, this.container.clientHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.1;
    this.container.appendChild(this.renderer.domElement);
  }

  private initControls(): void {
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.04;
    this.controls.minDistance = 4;
    this.controls.maxDistance = 35;
    this.controls.maxPolarAngle = Math.PI / 2.1; // Slight above-ground lock
    this.controls.target.set(0, 0.5, 0);
    this.controls.autoRotate = false; // We handle auto-orbit ourselves for smoother control

    // Track user interaction to pause auto-orbit
    const markInteraction = () => {
      this.lastInteractionTime = this.elapsedTime;
      this.autoOrbitEnabled = false;
    };
    this.renderer.domElement.addEventListener('pointerdown', markInteraction);
    this.renderer.domElement.addEventListener('wheel', markInteraction);
  }

  /**
   * Warm, cozy ambient lighting setup
   */
  private initLights(): void {
    // Soft ambient fill - warm tint
    const ambient = new THREE.AmbientLight(0xffeedd, 0.35);
    this.scene.add(ambient);

    // Main warm key light (top-down, slightly angled)
    const keyLight = new THREE.DirectionalLight(0xfff0e0, 0.7);
    keyLight.position.set(5, 15, 5);
    keyLight.castShadow = true;
    keyLight.shadow.mapSize.width = 2048;
    keyLight.shadow.mapSize.height = 2048;
    keyLight.shadow.camera.near = 0.5;
    keyLight.shadow.camera.far = 40;
    keyLight.shadow.camera.left = -15;
    keyLight.shadow.camera.right = 15;
    keyLight.shadow.camera.top = 15;
    keyLight.shadow.camera.bottom = -15;
    keyLight.shadow.bias = -0.001;
    this.scene.add(keyLight);

    // Cool fill from opposite side
    const fillLight = new THREE.DirectionalLight(0xaabbff, 0.2);
    fillLight.position.set(-8, 8, -8);
    this.scene.add(fillLight);

    // Warm point light in center (like a cozy lamp)
    const centerGlow = new THREE.PointLight(0xffcc88, 0.5, 20, 1.5);
    centerGlow.position.set(0, 3, 0);
    this.scene.add(centerGlow);

    // Hemisphere light for sky/ground color contrast
    const hemiLight = new THREE.HemisphereLight(0x8899cc, 0x443322, 0.25);
    this.scene.add(hemiLight);
  }

  /**
   * Create cozy room environment - soft floor, subtle walls
   */
  private initRoom(): void {
    // Warm wood-tone floor
    const floorGeom = new THREE.CircleGeometry(18, 64);
    const floorMat = new THREE.MeshStandardMaterial({
      color: 0x2a2535,
      roughness: 0.85,
      metalness: 0.05,
    });
    const floor = new THREE.Mesh(floorGeom, floorMat);
    floor.rotation.x = -Math.PI / 2;
    floor.receiveShadow = true;
    this.scene.add(floor);

    // Subtle circular rug in center
    const rugGeom = new THREE.CircleGeometry(6, 48);
    const rugMat = new THREE.MeshStandardMaterial({
      color: 0x3a2f4a,
      roughness: 0.95,
      metalness: 0.0,
    });
    const rug = new THREE.Mesh(rugGeom, rugMat);
    rug.rotation.x = -Math.PI / 2;
    rug.position.y = 0.005;
    rug.receiveShadow = true;
    this.scene.add(rug);

    // Soft ring around rug edge
    const rugEdgeGeom = new THREE.TorusGeometry(6, 0.08, 8, 64);
    const rugEdgeMat = new THREE.MeshStandardMaterial({
      color: 0x5865F2,
      roughness: 0.7,
      emissive: 0x5865F2,
      emissiveIntensity: 0.1,
    });
    const rugEdge = new THREE.Mesh(rugEdgeGeom, rugEdgeMat);
    rugEdge.rotation.x = -Math.PI / 2;
    rugEdge.position.y = 0.01;
    this.scene.add(rugEdge);
  }

  /**
   * Floating ambient particles for cozy atmosphere
   */
  private initParticles(): void {
    const count = 200;
    const positions = new Float32Array(count * 3);
    const sizes = new Float32Array(count);

    for (let i = 0; i < count; i++) {
      positions[i * 3] = (Math.random() - 0.5) * 30;
      positions[i * 3 + 1] = Math.random() * 8 + 0.5;
      positions[i * 3 + 2] = (Math.random() - 0.5) * 30;
      sizes[i] = Math.random() * 0.04 + 0.01;
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

    const material = new THREE.PointsMaterial({
      color: 0xffeedd,
      size: 0.06,
      transparent: true,
      opacity: 0.4,
      sizeAttenuation: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    });

    this.particles = new THREE.Points(geometry, material);
    this.scene.add(this.particles);
  }

  /**
   * Update room visualization based on backend config
   */
  private updateRoom(room: SceneGraph['room']): void {
    if (this.roomMesh) {
      this.scene.remove(this.roomMesh);
      this.roomMesh.traverse((child) => {
        if (child instanceof THREE.Mesh) {
          child.geometry.dispose();
          if (child.material instanceof THREE.Material) {
            child.material.dispose();
          }
        }
      });
    }

    // Subtle wireframe room boundary
    const { width, height, depth } = room.dimensions;
    const roomGeometry = new THREE.BoxGeometry(width, height, depth);
    const roomEdges = new THREE.EdgesGeometry(roomGeometry);
    const roomMaterial = new THREE.LineBasicMaterial({
      color: 0x5865F2,
      transparent: true,
      opacity: 0.15,
    });
    this.roomMesh = new THREE.LineSegments(roomEdges, roomMaterial);
    this.roomMesh.position.y = height / 2;
    this.scene.add(this.roomMesh);
  }

  /**
   * Update furniture with spawn animations
   */
  private updateFurniture(furnitureList: FurnitureNode[]): void {
    const currentIds = new Set(furnitureList.map((f) => f.id));

    // Remove furniture that no longer exists (with scale-down)
    for (const [id, obj] of this.furnitureObjects) {
      if (!currentIds.has(id)) {
        // Quick scale-down animation
        const startScale = obj.scale.x;
        const startTime = this.elapsedTime;
        const animateDespawn = () => {
          const t = Math.min((this.elapsedTime - startTime) * 3, 1);
          const scale = startScale * (1 - t);
          obj.scale.setScalar(Math.max(scale, 0.001));
          if (t >= 1) {
            this.scene.remove(obj);
            obj.traverse((child) => {
              if (child instanceof THREE.Mesh) {
                child.geometry.dispose();
                if (child.material instanceof THREE.Material) child.material.dispose();
              }
            });
          }
        };
        animateDespawn(); // Run immediately for simplicity; full animation happens via render loop scale
        this.scene.remove(obj);
        obj.traverse((child) => {
          if (child instanceof THREE.Mesh) {
            child.geometry.dispose();
            if (child.material instanceof THREE.Material) child.material.dispose();
          }
        });
        this.furnitureObjects.delete(id);
      }
    }

    furnitureList.forEach((furniture) => {
      let furnitureMesh = this.furnitureObjects.get(furniture.id);

      if (!furnitureMesh) {
        furnitureMesh = FurnitureFactory.getMesh(furniture.type) ?? undefined;
        if (!furnitureMesh) {
          console.error(`Unknown furniture type: ${furniture.type}`);
          return;
        }

        const position = toThreeVector3(furniture.position);
        const rotation = new THREE.Euler(furniture.rotation.x, furniture.rotation.y, furniture.rotation.z);
        furnitureMesh.position.copy(position);
        furnitureMesh.rotation.copy(rotation);

        // Start at scale 0 for spawn animation
        furnitureMesh.scale.setScalar(0.001);
        furnitureMesh.userData._spawnTime = this.elapsedTime;

        furnitureMesh.traverse((child) => {
          if (child instanceof THREE.Mesh) {
            child.castShadow = true;
            child.receiveShadow = true;
          }
        });

        this.scene.add(furnitureMesh);
        this.furnitureObjects.set(furniture.id, furnitureMesh);
      } else {
        // Smooth position/rotation update
        const position = toThreeVector3(furniture.position);
        const rotation = new THREE.Euler(furniture.rotation.x, furniture.rotation.y, furniture.rotation.z);
        furnitureMesh.position.lerp(position, this.config.lerpFactor);
        furnitureMesh.rotation.x = THREE.MathUtils.lerp(furnitureMesh.rotation.x, rotation.x, this.config.lerpFactor);
        furnitureMesh.rotation.y = THREE.MathUtils.lerp(furnitureMesh.rotation.y, rotation.y, this.config.lerpFactor);
        furnitureMesh.rotation.z = THREE.MathUtils.lerp(furnitureMesh.rotation.z, rotation.z, this.config.lerpFactor);
      }
    });
  }

  /**
   * Update users with spawn/despawn animations
   */
  private updateUsers(userList: UserNode[]): void {
    const currentIds = new Set(userList.map((u) => u.id));

    // Despawn users that left
    for (const [id, avatar] of this.userAvatars) {
      if (!currentIds.has(id)) {
        avatar.despawn(() => {
          this.scene.remove(avatar.mesh);
          avatar.dispose();
        });
        this.userAvatars.delete(id);
      }
    }

    // Update or create users
    userList.forEach((user) => {
      let avatar = this.userAvatars.get(user.id);

      if (!avatar) {
        const cachedTexture = this.avatarTextures.get(user.id);
        avatar = new UserAvatar(user, cachedTexture);
        this.scene.add(avatar.mesh);
        this.userAvatars.set(user.id, avatar);

        // Async texture load
        if (!cachedTexture && user.avatar) {
          loadTexture(user.avatar).then((texture) => {
            if (texture) {
              this.avatarTextures.set(user.id, texture);
              const existing = this.userAvatars.get(user.id);
              if (existing) existing.setAvatarTexture(texture);
            }
          });
        }
      } else {
        avatar.updateData(user);
      }
    });
  }

  /**
   * Main render loop
   */
  private renderLoop = (): void => {
    this.animationId = requestAnimationFrame(this.renderLoop);
    const deltaTime = this.clock.getDelta();
    this.elapsedTime += deltaTime;

    // Update controls
    this.controls.update();

    // Auto-orbit: resume after inactivity
    if (!this.autoOrbitEnabled && (this.elapsedTime - this.lastInteractionTime) > this.autoOrbitResumeDelay) {
      this.autoOrbitEnabled = true;
    }
    if (this.autoOrbitEnabled) {
      const angle = this.elapsedTime * this.autoOrbitSpeed;
      const radius = this.camera.position.length();
      const height = this.camera.position.y;
      // Gentle sway in height
      const sway = Math.sin(this.elapsedTime * 0.15) * 0.5;
      this.camera.position.x = Math.cos(angle) * radius * 0.7;
      this.camera.position.z = Math.sin(angle) * radius * 0.7;
      this.camera.position.y = height + sway * deltaTime;
      this.camera.lookAt(this.controls.target);
    }

    // Update all avatars
    this.userAvatars.forEach((avatar) => {
      avatar.update(this.config.lerpFactor, deltaTime);
    });

    // Animate furniture spawn (elastic scale-in)
    this.furnitureObjects.forEach((obj) => {
      if (obj.userData._spawnTime !== undefined && obj.scale.x < 1) {
        const age = this.elapsedTime - obj.userData._spawnTime;
        const t = Math.min(age * 2.5, 1);
        // Elastic ease
        const elastic = t === 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1;
        obj.scale.setScalar(elastic);
        if (t >= 1) {
          obj.scale.setScalar(1);
          delete obj.userData._spawnTime;
        }
      }
    });

    // Animate particles (gentle float)
    if (this.particles) {
      const positions = this.particles.geometry.attributes.position;
      for (let i = 0; i < positions.count; i++) {
        const y = positions.getY(i);
        positions.setY(i, y + Math.sin(this.elapsedTime * 0.3 + i) * 0.002);
        // Wrap particles that float too high
        if (positions.getY(i) > 10) positions.setY(i, 0.5);
      }
      positions.needsUpdate = true;
      // Gentle rotation
      this.particles.rotation.y = this.elapsedTime * 0.01;
    }

    // Render
    this.renderer.render(this.scene, this.camera);
  };

  private startRenderLoop(): void {
    this.renderLoop();
  }

  private handleResize(): void {
    const width = this.container.clientWidth;
    const height = this.container.clientHeight;
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }
}
