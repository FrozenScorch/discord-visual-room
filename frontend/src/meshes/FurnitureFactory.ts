import * as THREE from 'three';
import type { FurnitureType } from '../types';

/**
 * FurnitureFactory - Creates pre-loaded 3D meshes for all furniture types
 *
 * IMPORTANT: These must match the exact types in the shared types package.
 * The backend sends furniture type strings that must match these keys.
 */
export class FurnitureFactory {
  private static meshes: Map<FurnitureType, THREE.Object3D> = new Map();

  /**
   * Initialize all pre-loaded furniture meshes
   * Call this once at application startup
   */
  public static initialize(): void {
    this.meshes.set('COMPUTER_DESK', this.createComputerDesk());
    this.meshes.set('COUCH_2_SEATER', this.createCouch2Seater());
    this.meshes.set('COUCH_SINGLE', this.createCouchSingle());
    this.meshes.set('BAR_STOOL', this.createBarStool());
  }

  /**
   * Get a cloned instance of a furniture mesh
   * Each furniture placement gets its own clone
   */
  public static getMesh(type: FurnitureType): THREE.Object3D | null {
    const original = this.meshes.get(type);
    return original ? original.clone() : null;
  }

  /**
   * Get all available furniture types
   */
  public static getAvailableTypes(): FurnitureType[] {
    return Array.from(this.meshes.keys());
  }

  /**
   * Create a computer desk mesh
   * - Large flat surface for monitors/keyboard
   * - Legs at corners
   * - Color: Wood brown
   */
  private static createComputerDesk(): THREE.Object3D {
    const group = new THREE.Group();

    // Desk surface (main table)
    const surfaceGeom = new THREE.BoxGeometry(2, 0.1, 1);
    const woodMaterial = new THREE.MeshStandardMaterial({
      color: 0x8B4513, // Saddle brown
      roughness: 0.7,
      metalness: 0.1,
    });
    const surface = new THREE.Mesh(surfaceGeom, woodMaterial);
    surface.position.y = 0.75;
    surface.castShadow = true;
    surface.receiveShadow = true;
    group.add(surface);

    // Desk legs (4 corners)
    const legGeom = new THREE.BoxGeometry(0.08, 0.75, 0.08);
    const legPositions = [
      [-0.9, 0.375, -0.4],
      [0.9, 0.375, -0.4],
      [-0.9, 0.375, 0.4],
      [0.9, 0.375, 0.4],
    ];

    legPositions.forEach((pos) => {
      const leg = new THREE.Mesh(legGeom, woodMaterial);
      leg.position.set(pos[0], pos[1], pos[2]);
      leg.castShadow = true;
      group.add(leg);
    });

    // Monitor stand hint (small rectangle on desk)
    const monitorStandGeom = new THREE.BoxGeometry(0.3, 0.05, 0.2);
    const darkMaterial = new THREE.MeshStandardMaterial({
      color: 0x2a2a2a,
      roughness: 0.5,
      metalness: 0.7,
    });
    const monitorStand = new THREE.Mesh(monitorStandGeom, darkMaterial);
    monitorStand.position.set(0, 0.825, -0.3);
    group.add(monitorStand);

    return group;
  }

  /**
   * Create a 2-seater couch
   * - Wide seating area
   * - Backrest and armrests
   * - Color: Gray fabric
   */
  private static createCouch2Seater(): THREE.Object3D {
    const group = new THREE.Group();

    const fabricMaterial = new THREE.MeshStandardMaterial({
      color: 0x696969, // Dim gray
      roughness: 0.9,
      metalness: 0.0,
    });

    // Seat base
    const seatGeom = new THREE.BoxGeometry(2.5, 0.4, 1);
    const seat = new THREE.Mesh(seatGeom, fabricMaterial);
    seat.position.y = 0.2;
    seat.castShadow = true;
    seat.receiveShadow = true;
    group.add(seat);

    // Backrest
    const backGeom = new THREE.BoxGeometry(2.5, 0.6, 0.2);
    const back = new THREE.Mesh(backGeom, fabricMaterial);
    back.position.set(0, 0.7, -0.4);
    back.castShadow = true;
    group.add(back);

    // Left armrest
    const armGeom = new THREE.BoxGeometry(0.2, 0.5, 1);
    const leftArm = new THREE.Mesh(armGeom, fabricMaterial);
    leftArm.position.set(-1.15, 0.45, 0);
    leftArm.castShadow = true;
    group.add(leftArm);

    // Right armrest
    const rightArm = new THREE.Mesh(armGeom, fabricMaterial);
    rightArm.position.set(1.15, 0.45, 0);
    rightArm.castShadow = true;
    group.add(rightArm);

    // Two seat cushions
    const cushionGeom = new THREE.BoxGeometry(1.1, 0.15, 0.9);
    const cushionMaterial = new THREE.MeshStandardMaterial({
      color: 0x808080,
      roughness: 0.85,
    });

    const leftCushion = new THREE.Mesh(cushionGeom, cushionMaterial);
    leftCushion.position.set(-0.55, 0.475, 0);
    group.add(leftCushion);

    const rightCushion = new THREE.Mesh(cushionGeom, cushionMaterial);
    rightCushion.position.set(0.55, 0.475, 0);
    group.add(rightCushion);

    return group;
  }

  /**
   * Create a single-seat couch/armchair
   * - Smaller version of 2-seater
   * - Color: Blue fabric
   */
  private static createCouchSingle(): THREE.Object3D {
    const group = new THREE.Group();

    const fabricMaterial = new THREE.MeshStandardMaterial({
      color: 0x4682B4, // Steel blue
      roughness: 0.9,
      metalness: 0.0,
    });

    // Seat base
    const seatGeom = new THREE.BoxGeometry(1.2, 0.4, 1);
    const seat = new THREE.Mesh(seatGeom, fabricMaterial);
    seat.position.y = 0.2;
    seat.castShadow = true;
    seat.receiveShadow = true;
    group.add(seat);

    // Backrest
    const backGeom = new THREE.BoxGeometry(1.2, 0.6, 0.2);
    const back = new THREE.Mesh(backGeom, fabricMaterial);
    back.position.set(0, 0.7, -0.4);
    back.castShadow = true;
    group.add(back);

    // Left armrest
    const armGeom = new THREE.BoxGeometry(0.2, 0.5, 1);
    const leftArm = new THREE.Mesh(armGeom, fabricMaterial);
    leftArm.position.set(-0.6, 0.45, 0);
    leftArm.castShadow = true;
    group.add(leftArm);

    // Right armrest
    const rightArm = new THREE.Mesh(armGeom, fabricMaterial);
    rightArm.position.set(0.6, 0.45, 0);
    rightArm.castShadow = true;
    group.add(rightArm);

    // Seat cushion
    const cushionGeom = new THREE.BoxGeometry(0.9, 0.15, 0.9);
    const cushionMaterial = new THREE.MeshStandardMaterial({
      color: 0x5F9EA0,
      roughness: 0.85,
    });
    const cushion = new THREE.Mesh(cushionGeom, cushionMaterial);
    cushion.position.set(0, 0.475, 0);
    group.add(cushion);

    return group;
  }

  /**
   * Create a bar stool
   * - Round seat
   * - Tall pedestal base
   * - Footrest
   * - Color: Black metal with leather seat
   */
  private static createBarStool(): THREE.Object3D {
    const group = new THREE.Group();

    // Seat (cylinder)
    const seatGeom = new THREE.CylinderGeometry(0.3, 0.25, 0.1, 32);
    const leatherMaterial = new THREE.MeshStandardMaterial({
      color: 0x1a1a1a, // Near black
      roughness: 0.6,
      metalness: 0.1,
    });
    const seat = new THREE.Mesh(seatGeom, leatherMaterial);
    seat.position.y = 1.2;
    seat.castShadow = true;
    group.add(seat);

    // Center pole
    const poleGeom = new THREE.CylinderGeometry(0.03, 0.04, 1.2, 16);
    const metalMaterial = new THREE.MeshStandardMaterial({
      color: 0x2a2a2a,
      roughness: 0.3,
      metalness: 0.8,
    });
    const pole = new THREE.Mesh(poleGeom, metalMaterial);
    pole.position.y = 0.6;
    pole.castShadow = true;
    group.add(pole);

    // Footrest (ring)
    const footrestGeom = new THREE.TorusGeometry(0.15, 0.02, 8, 32);
    const footrest = new THREE.Mesh(footrestGeom, metalMaterial);
    footrest.position.y = 0.4;
    footrest.rotation.x = Math.PI / 2;
    group.add(footrest);

    // Base (flat cylinder)
    const baseGeom = new THREE.CylinderGeometry(0.25, 0.3, 0.05, 32);
    const base = new THREE.Mesh(baseGeom, metalMaterial);
    base.position.y = 0.025;
    base.receiveShadow = true;
    group.add(base);

    return group;
  }
}
