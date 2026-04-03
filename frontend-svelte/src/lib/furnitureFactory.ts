import * as THREE from 'three';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/** The four furniture types the LLM may assign to users */
export type FurnitureType = 'COMPUTER_DESK' | 'COUCH_2_SEATER' | 'COUCH_SINGLE' | 'BAR_STOOL';

// ---------------------------------------------------------------------------
// FurnitureFactory
// ---------------------------------------------------------------------------

/**
 * FurnitureFactory - Creates cute, rounded 3D meshes for all furniture types.
 * Uses soft colors, rounded geometry, and subtle glow for a cozy aesthetic.
 *
 * Usage:
 *   FurnitureFactory.initialize();       // call once
 *   const mesh = FurnitureFactory.getMesh('COMPUTER_DESK');
 *   scene.add(mesh);
 */
export class FurnitureFactory {
  private static meshes: Map<FurnitureType, THREE.Object3D> = new Map();

  public static initialize(): void {
    this.meshes.set('COMPUTER_DESK', this.createComputerDesk());
    this.meshes.set('COUCH_2_SEATER', this.createCouch2Seater());
    this.meshes.set('COUCH_SINGLE', this.createCouchSingle());
    this.meshes.set('BAR_STOOL', this.createBarStool());
  }

  public static getMesh(type: FurnitureType): THREE.Object3D | null {
    const original = this.meshes.get(type);
    return original ? original.clone() : null;
  }

  public static getAvailableTypes(): FurnitureType[] {
    return Array.from(this.meshes.keys());
  }

  // -----------------------------------------------------------------------
  // Computer Desk - Warm wood with a tiny monitor and soft glow
  // -----------------------------------------------------------------------
  private static createComputerDesk(): THREE.Object3D {
    const group = new THREE.Group();

    // Desk surface - warm maple
    const surfaceGeom = new THREE.BoxGeometry(2.2, 0.12, 1.1);
    surfaceGeom.translate(0, 0, 0);
    const woodMat = new THREE.MeshStandardMaterial({
      color: 0xd4a574, // warm maple
      roughness: 0.6,
      metalness: 0.05,
    });
    const surface = new THREE.Mesh(surfaceGeom, woodMat);
    surface.position.y = 0.75;
    surface.castShadow = true;
    surface.receiveShadow = true;
    group.add(surface);

    // Rounded legs
    const legGeom = new THREE.CylinderGeometry(0.04, 0.05, 0.75, 8);
    const legMat = new THREE.MeshStandardMaterial({ color: 0xc09060, roughness: 0.5 });
    const legPositions: [number, number][] = [
      [-0.95, -0.45],
      [0.95, -0.45],
      [-0.95, 0.45],
      [0.95, 0.45],
    ];
    legPositions.forEach(([x, z]) => {
      const leg = new THREE.Mesh(legGeom, legMat);
      leg.position.set(x, 0.375, z);
      leg.castShadow = true;
      group.add(leg);
    });

    // Cute tiny monitor
    const monitorGeom = new THREE.BoxGeometry(0.6, 0.4, 0.04);
    const screenMat = new THREE.MeshStandardMaterial({
      color: 0x88bbff,
      roughness: 0.2,
      metalness: 0.1,
      emissive: 0x4488cc,
      emissiveIntensity: 0.3,
    });
    const monitor = new THREE.Mesh(monitorGeom, screenMat);
    monitor.position.set(0, 1.05, -0.35);
    group.add(monitor);

    // Monitor stand
    const standGeom = new THREE.CylinderGeometry(0.02, 0.04, 0.15, 8);
    const standMat = new THREE.MeshStandardMaterial({
      color: 0x444444,
      metalness: 0.7,
      roughness: 0.3,
    });
    const stand = new THREE.Mesh(standGeom, standMat);
    stand.position.set(0, 0.88, -0.35);
    group.add(stand);

    // Keyboard
    const kbGeom = new THREE.BoxGeometry(0.5, 0.03, 0.15);
    const kbMat = new THREE.MeshStandardMaterial({
      color: 0x333333,
      roughness: 0.4,
      metalness: 0.3,
    });
    const kb = new THREE.Mesh(kbGeom, kbMat);
    kb.position.set(0, 0.82, 0);
    group.add(kb);

    return group;
  }

  // -----------------------------------------------------------------------
  // 2-Seater Couch - Plush, rounded, cozy purple
  // -----------------------------------------------------------------------
  private static createCouch2Seater(): THREE.Object3D {
    const group = new THREE.Group();

    const fabricMat = new THREE.MeshStandardMaterial({
      color: 0x7b68ae, // Soft purple
      roughness: 0.9,
      metalness: 0.0,
    });

    // Seat base - rounded
    const seatGeom = new THREE.BoxGeometry(2.6, 0.5, 1.1);
    const seat = new THREE.Mesh(seatGeom, fabricMat);
    seat.position.y = 0.25;
    seat.castShadow = true;
    seat.receiveShadow = true;
    group.add(seat);

    // Backrest - taller, rounded
    const backMat = new THREE.MeshStandardMaterial({ color: 0x6a5a9e, roughness: 0.9 });
    const backGeom = new THREE.BoxGeometry(2.6, 0.7, 0.25);
    const back = new THREE.Mesh(backGeom, backMat);
    back.position.set(0, 0.8, -0.42);
    back.castShadow = true;
    group.add(back);

    // Armrests
    const armGeom = new THREE.BoxGeometry(0.25, 0.5, 1.1);
    const leftArm = new THREE.Mesh(armGeom, backMat);
    leftArm.position.set(-1.2, 0.5, 0);
    leftArm.castShadow = true;
    group.add(leftArm);
    const rightArm = new THREE.Mesh(armGeom, backMat);
    rightArm.position.set(1.2, 0.5, 0);
    rightArm.castShadow = true;
    group.add(rightArm);

    // Two cute cushions
    const cushionMat = new THREE.MeshStandardMaterial({ color: 0x9b88ce, roughness: 0.85 });
    const cushionGeom = new THREE.SphereGeometry(0.35, 16, 12);
    [-0.5, 0.5].forEach((x) => {
      const cushion = new THREE.Mesh(cushionGeom, cushionMat);
      cushion.position.set(x, 0.55, 0);
      cushion.scale.set(1.4, 0.6, 1);
      group.add(cushion);
    });

    return group;
  }

  // -----------------------------------------------------------------------
  // Single Couch / Armchair - Cozy teal blue
  // -----------------------------------------------------------------------
  private static createCouchSingle(): THREE.Object3D {
    const group = new THREE.Group();

    const fabricMat = new THREE.MeshStandardMaterial({
      color: 0x5ba4b5, // Teal blue
      roughness: 0.9,
      metalness: 0.0,
    });

    const seatGeom = new THREE.BoxGeometry(1.3, 0.5, 1.1);
    const seat = new THREE.Mesh(seatGeom, fabricMat);
    seat.position.y = 0.25;
    seat.castShadow = true;
    seat.receiveShadow = true;
    group.add(seat);

    const backMat = new THREE.MeshStandardMaterial({ color: 0x4a8e9e, roughness: 0.9 });
    const backGeom = new THREE.BoxGeometry(1.3, 0.7, 0.22);
    const back = new THREE.Mesh(backGeom, backMat);
    back.position.set(0, 0.8, -0.42);
    back.castShadow = true;
    group.add(back);

    const armGeom = new THREE.BoxGeometry(0.2, 0.5, 1.1);
    [-0.65, 0.65].forEach((x) => {
      const arm = new THREE.Mesh(armGeom, backMat);
      arm.position.set(x, 0.5, 0);
      arm.castShadow = true;
      group.add(arm);
    });

    // Seat cushion
    const cushionMat = new THREE.MeshStandardMaterial({ color: 0x6fbfcf, roughness: 0.85 });
    const cushion = new THREE.Mesh(new THREE.SphereGeometry(0.3, 16, 12), cushionMat);
    cushion.position.set(0, 0.55, 0);
    cushion.scale.set(1.5, 0.5, 1.3);
    group.add(cushion);

    return group;
  }

  // -----------------------------------------------------------------------
  // Bar Stool - Cute round seat with chrome base
  // -----------------------------------------------------------------------
  private static createBarStool(): THREE.Object3D {
    const group = new THREE.Group();

    // Seat - puffy round cushion
    const seatGeom = new THREE.SphereGeometry(0.3, 24, 16);
    const seatMat = new THREE.MeshStandardMaterial({
      color: 0xe8a87c, // Warm peach
      roughness: 0.7,
      metalness: 0.05,
    });
    const seat = new THREE.Mesh(seatGeom, seatMat);
    seat.position.y = 1.15;
    seat.scale.set(1, 0.4, 1);
    seat.castShadow = true;
    group.add(seat);

    // Chrome pole
    const poleMat = new THREE.MeshStandardMaterial({
      color: 0xcccccc,
      roughness: 0.15,
      metalness: 0.9,
    });
    const pole = new THREE.Mesh(
      new THREE.CylinderGeometry(0.025, 0.035, 1.1, 12),
      poleMat
    );
    pole.position.y = 0.55;
    pole.castShadow = true;
    group.add(pole);

    // Footrest ring
    const footrest = new THREE.Mesh(new THREE.TorusGeometry(0.18, 0.015, 8, 24), poleMat);
    footrest.position.y = 0.4;
    footrest.rotation.x = Math.PI / 2;
    group.add(footrest);

    // Base
    const base = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.28, 0.04, 24), poleMat);
    base.position.y = 0.02;
    base.receiveShadow = true;
    group.add(base);

    return group;
  }
}
