import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ToastrService } from 'ngx-toastr';
import { Router, NavigationEnd, ActivatedRoute} from "@angular/router";
import { filter } from "rxjs/operators";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { ClipboardService } from "ngx-clipboard";
import { Title } from '@angular/platform-browser';
import { CommonService } from '../../../common/services/common.service';
import { StoreSettingtService } from 'src/app/common/services/store-setting.service';

@Component({
  selector: 'app-switch-detail',
  templateUrl: './switch-detail.component.html',
  styleUrls: ['./switch-detail.component.css']
})
export class SwitchDetailComponent implements OnInit, AfterViewInit,OnDestroy {
  switchDetail:any = {};
  switch_id: string;
  name: string;
  address: string;
  hostname: string;
  description: string;
  state: string;
  openedTab : string = 'port';
  currentRoute: string = 'switch-details';
  clipBoardItems = {
    sourceSwitchName:"",
    sourceSwitch:"",
    targetSwitchName:""
  };
  switchId = null;
  hasStoreSetting;
  settingSubscriber =null;

  descrepancyData = {
    status:{
      controller: "-",
      inventory:"-"
    }
  }
  isLoaderActive = true;

  statusDescrepancy = false;


  constructor(private switchService:SwitchService,
        private maskPipe: SwitchidmaskPipe,
        private toastr: ToastrService,
        private router: Router,
        private route: ActivatedRoute,
        private loaderService: LoaderService,
        private clipboardService: ClipboardService,
        private titleService: Title,
        private commonService: CommonService,
        private storeSwitchService:StoreSettingtService
        ) {

      this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
    }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - View Switch');

    this.route.params.subscribe(params => {
      this.switchId = params['id'];
      this.getSwitchDetail(params['id']);
    });

    if(this.router.url.includes("/port")){
         this.router.navigated = false;
        this.router.navigate([this.router.url]);
    }

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd)).pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        let tempRoute : any = event;
        if(tempRoute.url.includes("/port")){
          this.currentRoute = 'port-details';
        }
        else{
          this.currentRoute = 'switch-details';
        }
   
      });     
  }

  maskSwitchId(switchType, e) {
    if (e.target.checked) {
      this.switchDetail.switch_id = this.maskPipe.transform(this.switchDetail.switch_id,'legacy');
    } else {
      this.switchDetail.switch_id = this.maskPipe.transform(this.switchDetail.switch_id,'kilda');
    }

    this.clipBoardItems.sourceSwitch = this.switchDetail.switch_id;

  }

  toggleTab(tab, enableLoader = false){
    this.openedTab = tab;
    if(enableLoader){
      this.isLoaderActive = true;
    }else{
      this.isLoaderActive = false;
    }
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  ngAfterViewInit(){
    
  }

  getSwitchDetail(switchId){

    this.loaderService.show("Loading Switch Details");

    this.settingSubscriber = this.storeSwitchService.switchSettingReceiver.subscribe(setting=>{
      this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
      
      this.switchService.getSwitchDetail(switchId).subscribe((retrievedSwitchObject : any)=>{
        if(!retrievedSwitchObject){
          this.loaderService.hide();
          this.toastr.error("No Switch Found",'Error');
          this.router.navigate([
            "/switches"
          ]);        
        }else{
        this.switchDetail = retrievedSwitchObject;
        localStorage.setItem('switchDetailsJSON',JSON.stringify(retrievedSwitchObject));
        this.switch_id =retrievedSwitchObject.switch_id;
        this.name =retrievedSwitchObject.name;
        this.address =retrievedSwitchObject.address;
        this.hostname =retrievedSwitchObject.hostname;
        this.description =retrievedSwitchObject.description;
        this.state =retrievedSwitchObject.state;
        this.clipBoardItems = Object.assign(this.clipBoardItems,{
            
            sourceSwitchName: retrievedSwitchObject.name,
            sourceSwitch: this.switch_id,
            targetSwitchName: retrievedSwitchObject.hostname
            
          });
          this.loaderService.hide();
          if(retrievedSwitchObject['discrepancy'] && (retrievedSwitchObject['discrepancy']['status'])){
            if(retrievedSwitchObject['discrepancy']['status']){
              this.statusDescrepancy  = true;
              this.descrepancyData.status.controller = (typeof(retrievedSwitchObject['discrepancy']['status-value']['controller-status'])!='undefined') ?  retrievedSwitchObject['discrepancy']['status-value']['controller-status'] : "-";
              this.descrepancyData.status.inventory = (typeof(retrievedSwitchObject['discrepancy']['status-value']['inventory-status'])!='undefined') ?  retrievedSwitchObject['discrepancy']['status-value']['inventory-status'] : "-";
            }
            
          }
        }
      },err=>{

          this.loaderService.hide();
          this.toastr.error("No Switch Found",'Error');
          this.router.navigate(['/switches']);

      });
    });
    let query = {_:new Date().getTime()};
    this.storeSwitchService.checkSwitchStoreDetails(query);

    
    
  }

  ngOnDestroy(){
    if(this.settingSubscriber){
      this.settingSubscriber.unsubscribe();
      this.settingSubscriber = null;
    }
  }
}


