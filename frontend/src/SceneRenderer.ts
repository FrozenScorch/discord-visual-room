import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { SceneGraph, FurnitureNode, UserNode, RendererConfig } from './types';
import { FurnitureFactory } from './meshes/FurnitureFactory';
import { UserAvatar } from './meshes/UserAvatar';
import { toThreeVector3, loadTexture } from './utils/sceneUtils';

/**
 * SceneRenderer - Main Three.js scene manager
 *
 * Architecture: DUMB RENDERER
 * - Maintains NO state
 * - Renders whatever SceneGraph backend sends
 * - Updates scene based on incoming SCENE_UPDATE messages
 */
export class SceneRenderer {
  private scene: THREE.Scene;
  private camera: THREE.PerspectiveCamera;
  private renderer: THREE.WebGLRenderer;
  private controls: OrbitControls;

  // Scene objects tracked by ID
  private furnitureObjects: Map<string, THREE.Object3D> = new Map();
  private userAvatars: Map<string, UserAvatar> = new Map();
  private avatarTextures: Map<string, THREE.Texture> = new Map();

  // Room visualization
  private roomMesh: THREE.Object3D | null = null;

  // Animation
  private animationId: number | null = null;
  private clock: THREE.Clock = new THREE.Clock();

  // Configuration
  private config: RendererConfig;

  // Container
  private container: HTMLElement;

  constructor(container: HTMLElement, config: Partial<RendererConfig> = {}) {
    this.container = container;

    // Default configuration
    this.config = {
      wsUrl: config.wsUrl || 'ws://localhost:8080',
      cameraFov: config.cameraFov || 60,
      cameraNear: config.cameraNear || 0.1,
      cameraFar: config.cameraFar || 1000,
      lerpFactor: config.lerpFactor || 0.1,
    };

    // Initialize Three.js
    this.initScene();
    this.initCamera();
    this.initRenderer();
    this.initControls();
    this.initLights();
    this.initFloor();

    // Initialize furniture meshes
    FurnitureFactory.initialize();

    // Start render loop
    this.startRenderLoop();

    // Handle window resize
    window.addEventListener('resize', this.handleResize.bind(this));
  }

  /**
   * Update scene with new SceneGraph from backend
   * This is the main entry point for backend updates
   */
  public updateScene(sceneGraph: SceneGraph): void {
    // Update room visualization
    this.updateRoom(sceneGraph.room);

    // Update furniture
    this.updateFurniture(sceneGraph.furniture);

    // Update users
    this.updateUsers(sceneGraph.users);

    // Log update
    console.log(
      `Scene updated: ${sceneGraph.furniture.length} furniture, ${sceneGraph.users.length} users`
    );
  }

  /**
   * Get the current Three.js scene (for debugging)
   */
  public getScene(): THREE.Scene {
    return this.scene;
  }

  /**
   * Dispose of all resources
   */
  public dispose(): void {
    // Stop render loop
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
      this.animationId = null;
    }

    // Dispose of all user avatars
    this.userAvatars.forEach((avatar) => avatar.dispose());
    this.userAvatars.clear();

    // Dispose of avatar textures
    this.avatarTextures.forEach((texture) => texture.dispose());
    this.avatarTextures.clear();

    // Dispose of furniture
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

    // Dispose controls
    this.controls.dispose();

    // Dispose renderer
    this.renderer.dispose();

    // Remove resize listener
    window.removeEventListener('resize', this.handleResize.bind(this));
  }

  /**
   * Initialize Three.js scene
   */
  private initScene(): void {
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x1a1a2e); // Dark blue-purple
    this.scene.fog = new THREE.Fog(0x1a1a2e, 20, 100);
  }

  /**
   * Initialize camera
   */
  private initCamera(): void {
    const aspect = this.container.clientWidth / this.container.clientHeight;
    this.camera = new THREE.PerspectiveCamera(
      this.config.cameraFov,
      aspect,
      this.config.cameraNear,
      this.config.cameraFar
    );

    // Position camera for good initial view
    this.camera.position.set(15, 12, 15);
    this.camera.lookAt(0, 0, 0);
  }

  /**
   * Initialize WebGL renderer
   */
  private initRenderer(): void {
    this.renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: true,
    });

    this.renderer.setSize(this.container.clientWidth, this.container.clientHeight);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    this.container.appendChild(this.renderer.domElement);
  }

  /**
   * Initialize orbit controls
   */
  private initControls(): void {
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.05;
    this.controls.minDistance = 5;
    this.controls.maxDistance = 50;
    this.controls.maxPolarAngle = Math.PI / 2; // Don't go below ground
    this.controls.target.set(0, 0, 0);
  }

  /**
   * Initialize scene lighting
   */
  private initLights(): void {
    // Ambient light (soft fill)
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.4);
    this.scene.add(ambientLight);

    // Main directional light (sun-like)
    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8);
    directionalLight.position.set(10, 20, 10);
    directionalLight.castShadow = true;
    directionalLight.shadow.mapSize.width = 2048;
    directionalLight.shadow.mapSize.height = 2048;
    directionalLight.shadow.camera.near = 0.5;
    directionalLight.shadow.camera.far = 50;
    directionalLight.shadow.camera.left = -20;
    directionalLight.shadow.camera.right = 20;
    directionalLight.shadow.camera.top = 20;
    directionalLight.shadow.camera.bottom = -20;
    this.scene.add(directionalLight);

    // Fill light
    const fillLight = new THREE.DirectionalLight(0x8888ff, 0.3);
    fillLight.position.set(-10, 10, -10);
    this.scene.add(fillLight);
  }

  /**
   * Initialize floor
   */
  private initFloor(): void {
    const floorGeometry = new THREE.PlaneGeometry(100, 100);
    const floorMaterial = new THREE.MeshStandardMaterial({
      color: 0x2a2a3a,
      roughness: 0.8,
      metalness: 0.2,
    });
    const floor = new THREE.Mesh(floorGeometry, floorMaterial);
    floor.rotation.x = -Math.PI / 2;
    floor.receiveShadow = true;
    this.scene.add(floor);

    // Add grid helper
    const gridHelper = new THREE.GridHelper(50, 50, 0x444444, 0x333333);
    this.scene.add(gridHelper);
  }

  /**
   * Update room visualization
   */
  private updateRoom(room: SceneGraph['room']): void {
    // Remove old room mesh if exists
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

    // Create new room visualization (simple wireframe box)
    const { width, height, depth } = room.dimensions;
    const roomGeometry = new THREE.BoxGeometry(width, height, depth);
    const roomEdges = new THREE.EdgesGeometry(roomGeometry);
    const roomMaterial = new THREE.LineBasicMaterial({ color: 0x4a4a6a, linewidth: 2 });
    this.roomMesh = new THREE.LineSegments(roomEdges, roomMaterial);
    this.roomMesh.position.y = height / 2;
    this.scene.add(this.roomMesh);
  }

  /**
   * Update furniture in scene
   */
  private updateFurniture(furnitureList: FurnitureNode[]): void {
    // Track current furniture IDs
    const currentIds = new Set(furnitureList.map((f) => f.id));

    // Remove furniture that no longer exists
    for (const [id, obj] of this.furnitureObjects) {
      if (!currentIds.has(id)) {
        this.scene.remove(obj);
        obj.traverse((child) => {
          if (child instanceof THREE.Mesh) {
            child.geometry.dispose();
            if (child.material instanceof THREE.Material) {
              child.material.dispose();
            }
          }
        });
        this.furnitureObjects.delete(id);
      }
    }

    // Update or create furniture
    furnitureList.forEach((furniture) => {
      let furnitureMesh = this.furnitureObjects.get(furniture.id);

      if (!furnitureMesh) {
        // Create new furniture
        furnitureMesh = FurnitureFactory.getMesh(furniture.type);
        if (!furnitureMesh) {
          console.error(`Unknown furniture type: ${furniture.type}`);
          return;
        }

        // Set position and rotation
        const position = toThreeVector3(furniture.position);
        const rotation = new THREE.Euler(
          furniture.rotation.x,
          furniture.rotation.y,
          furniture.rotation.z
        );

        furnitureMesh.position.copy(position);
        furnitureMesh.rotation.copy(rotation);

        // Enable shadows
        furnitureMesh.traverse((child) => {
          if (child instanceof THREE.Mesh) {
            child.castShadow = true;
            child.receiveShadow = true;
          }
        });

        // Add to scene and track
        this.scene.add(furnitureMesh);
        this.furnitureObjects.set(furniture.id, furnitureMesh);
      } else {
        // Update existing furniture position/rotation
        const position = toThreeVector3(furniture.position);
        const rotation = new THREE.Euler(
          furniture.rotation.x,
          furniture.rotation.y,
          furniture.rotation.z
        );

        // Smooth update
        furnitureMesh.position.lerp(position, this.config.lerpFactor);
        furnitureMesh.rotation.x = THREE.MathUtils.lerp(
          furnitureMesh.rotation.x,
          rotation.x,
          this.config.lerpFactor
        );
        furnitureMesh.rotation.y = THREE.MathUtils.lerp(
          furnitureMesh.rotation.y,
          rotation.y,
          this.config.lerpFactor
        );
        furnitureMesh.rotation.z = THREE.MathUtils.lerp(
          furnitureMesh.rotation.z,
          rotation.z,
          this.config.lerpFactor
        );
      }
    });
  }

  /**
   * Update users in scene
   */
  private updateUsers(userList: UserNode[]): void {
    // Track current user IDs
    const currentIds = new Set(userList.map((u) => u.id));

    // Remove users that no longer exist
    for (const [id, avatar] of this.userAvatars) {
      if (!currentIds.has(id)) {
        this.scene.remove(avatar.mesh);
        avatar.dispose();
        this.userAvatars.delete(id);
      }
    }

    // Update or create users
    userList.forEach(async (user) => {
      let avatar = this.userAvatars.get(user.id);

      if (!avatar) {
        // Load avatar texture
        let avatarTexture = this.avatarTextures.get(user.id);
        if (!avatarTexture && user.avatar) {
          avatarTexture = await loadTexture(user.avatar);
          if (avatarTexture) {
            this.avatarTextures.set(user.id, avatarTexture);
          }
        }

        // Create new avatar
        avatar = new UserAvatar(user, avatarTexture);
        this.scene.add(avatar.mesh);
        this.userAvatars.set(user.id, avatar);
      } else {
        // Update existing avatar
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

    // Update controls
    this.controls.update();

    // Update all avatars
    this.userAvatars.forEach((avatar) => {
      avatar.update(this.config.lerpFactor, deltaTime);
    });

    // Render scene
    this.renderer.render(this.scene, this.camera);
  };

  /**
   * Start render loop
   */
  private startRenderLoop(): void {
    this.renderLoop();
  }

  /**
   * Handle window resize
   */
  private handleResize(): void {
    const width = this.container.clientWidth;
    const height = this.container.clientHeight;

    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();

    this.renderer.setSize(width, height);
  }
}
