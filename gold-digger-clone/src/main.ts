import Phaser from 'phaser';
import { BootScene } from './scenes/BootScene';
new Phaser.Game({type:Phaser.AUTO,width:720,height:1280,scene:[BootScene]});